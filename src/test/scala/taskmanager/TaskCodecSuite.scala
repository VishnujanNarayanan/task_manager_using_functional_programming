package taskmanager

/** Covers the persistence codec. `TaskCodec` is pure and total, so all of this
  * runs under Node with no browser and no storage -- the effectful half lives
  * in `TaskStorage`, which is deliberately thin enough to read instead.
  */
class TaskCodecSuite extends munit.FunSuite {

  private val sample = List(
    Task(1, "Buy milk", TaskDate(2026, 8, 15), TaskTime(9, 0), Priority.High, completed = false),
    Task(2, "Call the dentist", TaskDate(2026, 11, 31), TaskTime(23, 59), Priority.Medium, completed = true),
    Task(3, "Write tests", TaskDate(2027, 0, 1), TaskTime(0, 0), Priority.Low, completed = false)
  )

  // --- round trip ------------------------------------------------------

  test("a list survives an encode/decode round trip unchanged") {
    assertEquals(TaskCodec.decode(TaskCodec.encode(sample)), Right(sample))
  }

  test("an empty list round trips, and is distinct from having nothing stored") {
    // A visitor who deleted every task must not be handed the seed list back.
    assertEquals(TaskCodec.decode(TaskCodec.encode(Nil)), Right(List.empty[Task]))
  }

  test("every priority round trips") {
    val all = Priority.values.toList.zipWithIndex.map { case (p, i) =>
      Task(i, s"task $i", TaskDate(2026, 0, 1), TaskTime(0, 0), p, completed = false)
    }
    assertEquals(TaskCodec.decode(TaskCodec.encode(all)), Right(all))
  }

  test("both completion states round trip") {
    val both = List(
      Task(1, "done", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, completed = true),
      Task(2, "not done", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, completed = false)
    )
    assertEquals(TaskCodec.decode(TaskCodec.encode(both)), Right(both))
  }

  test("titles needing escaping survive the round trip") {
    // Hand-rolling the string escaping is exactly how a codec corrupts data, so
    // this pins down that JSON does it for us.
    val awkward = List(
      Task(1, """He said "hello" to me""", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, false),
      Task(2, "line one\nline two\twith a tab", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, false),
      Task(3, "back\\slash and emoji 🎯 and ünïcødé", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, false),
      Task(4, "", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, false)
    )
    assertEquals(TaskCodec.decode(TaskCodec.encode(awkward)), Right(awkward))
  }

  test("order is preserved") {
    val decoded = TaskCodec.decode(TaskCodec.encode(sample)).getOrElse(Nil)
    assertEquals(decoded.map(_.id), List(1, 2, 3))
  }

  test("the encoded payload carries a version") {
    assert(TaskCodec.encode(Nil).contains("\"v\":1"), TaskCodec.encode(Nil))
  }

  // --- decoding what we should not trust --------------------------------

  test("input that is not JSON is rejected, not thrown") {
    assert(TaskCodec.decode("not json at all").isLeft)
    assert(TaskCodec.decode("").isLeft)
    assert(TaskCodec.decode("{").isLeft)
  }

  test("valid JSON of the wrong shape is rejected") {
    assert(TaskCodec.decode("""{"v":1}""").isLeft, "no tasks array")
    assert(TaskCodec.decode("""{"v":1,"tasks":"nope"}""").isLeft, "tasks is not an array")
    assert(TaskCodec.decode("""{"tasks":[]}""").isLeft, "no version")
    assert(TaskCodec.decode("""[]""").isLeft, "top level is an array")
  }

  test("a payload from a future version is rejected by version, not by shape") {
    TaskCodec.decode("""{"v":99,"tasks":[]}""") match {
      case Left(DecodeError.UnsupportedVersion(99)) => ()
      case other => fail(s"expected UnsupportedVersion(99), got: $other")
    }
  }

  test("a task missing a field is rejected") {
    val missingTitle = """{"v":1,"tasks":[{"id":1,"year":2026,"month":0,"day":1,"hour":0,"minute":0,"priority":"Low","completed":false}]}"""
    assert(TaskCodec.decode(missingTitle).isLeft)
  }

  test("a task with a field of the wrong type is rejected") {
    val idIsAString = """{"v":1,"tasks":[{"id":"1","title":"x","year":2026,"month":0,"day":1,"hour":0,"minute":0,"priority":"Low","completed":false}]}"""
    assert(TaskCodec.decode(idIsAString).isLeft)
  }

  test("an unknown priority is rejected rather than defaulted") {
    val bogus = """{"v":1,"tasks":[{"id":1,"title":"x","year":2026,"month":0,"day":1,"hour":0,"minute":0,"priority":"Urgent","completed":false}]}"""
    TaskCodec.decode(bogus) match {
      case Left(DecodeError.WrongShape(msg)) => assert(msg.contains("Urgent"), msg)
      case other => fail(s"expected WrongShape naming the value, got: $other")
    }
  }

  test("one unreadable task fails the whole payload") {
    // Silently dropping it would lose a visitor's task with no explanation.
    val oneBad = """{"v":1,"tasks":[
      {"id":1,"title":"fine","year":2026,"month":0,"day":1,"hour":0,"minute":0,"priority":"Low","completed":false},
      {"id":2,"title":"broken","year":2026,"month":0,"day":1,"hour":0,"minute":0,"priority":"Nope","completed":false}
    ]}"""
    assert(TaskCodec.decode(oneBad).isLeft)
  }

  // --- nextId ----------------------------------------------------------

  test("nextId starts at 1 for an empty list") {
    assertEquals(TaskCodec.nextId(Nil), 1)
  }

  test("nextId is one past the highest id, not the length") {
    // The distinction that matters: after deleting from the middle, length and
    // max disagree, and length would hand back an id already in use.
    val gappy = List(
      Task(1, "a", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, false),
      Task(7, "b", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, false)
    )
    assertEquals(gappy.length, 2)
    assertEquals(TaskCodec.nextId(gappy), 8)
  }

  test("nextId ignores order") {
    val descending = List(
      Task(9, "a", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, false),
      Task(2, "b", TaskDate(2026, 0, 1), TaskTime(0, 0), Priority.Low, false)
    )
    assertEquals(TaskCodec.nextId(descending), 10)
  }

  test("ids stay unique across a save, reload and add") {
    // The regression the derivation exists to prevent: a stored counter reset
    // on reload hands out an id that is already taken, after which toggling one
    // task toggles two.
    val stored  = TaskCodec.decode(TaskCodec.encode(sample)).getOrElse(Nil)
    val added   = Main.addTask(stored, "after reload", TaskDate(2026, 8, 15),
                               TaskTime(12, 0), Priority.Low, TaskCodec.nextId(stored))
    val ids     = added.map(_.id)

    assertEquals(ids.distinct.length, ids.length, s"ids must stay unique, got: $ids")
    assertEquals(ids.last, 4)
  }

  test("toggling after a reload affects exactly one task") {
    val stored = TaskCodec.decode(TaskCodec.encode(sample)).getOrElse(Nil)
    val added  = Main.addTask(stored, "after reload", TaskDate(2026, 8, 15),
                              TaskTime(12, 0), Priority.Low, TaskCodec.nextId(stored))
    val before = added.count(_.completed)

    assertEquals(Main.toggleTask(added, 4).count(_.completed), before + 1)
  }
}
