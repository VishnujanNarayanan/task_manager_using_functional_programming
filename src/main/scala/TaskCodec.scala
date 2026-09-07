package taskmanager

import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.util.Try

/** Why a stored payload could not be read back.
  *
  * The application had no failure mode at all before persistence: every
  * transformation was total. Reading somebody else's browser storage is the
  * first place that stops being true, so decoding returns an `Either` rather
  * than throwing -- a payload we cannot read must cost a visitor their tasks,
  * never the whole app.
  */
enum DecodeError:
  case NotJson(message: String)
  case WrongShape(message: String)
  case UnsupportedVersion(found: Int)

/** What was found in storage at startup.
  *
  * `Empty` and `Loaded(Nil)` are deliberately different: nothing stored is a
  * first visit and gets the seed list, whereas an empty list is somebody who
  * deleted every task and must not have the seeds pushed back at them.
  *
  * `Unreadable` exists because discarding is not good enough. Falling straight
  * back to the seeds means the next edit writes over a payload we merely failed
  * to parse -- the data was recoverable right up until we quietly destroyed it.
  */
enum LoadResult:
  case Loaded(tasks: List[Task])
  case Empty
  case Unreadable(error: DecodeError)

/** Pure, total translation between `List[Task]` and the string that goes into
  * storage. No I/O happens here -- see `TaskStorage` for that. Keeping the two
  * apart is what lets the whole codec be unit-tested with no browser.
  */
object TaskCodec {

  /** Namespaced so it cannot collide with anything else on the origin. */
  val StorageKey: String = "functional-task-manager.v1.tasks"

  /** Bumped whenever the stored shape changes. Without it, adding a field to
    * `Task` would silently fail to decode for every existing visitor.
    */
  val Version: Int = 1

  /** The next free id, derived from the list rather than counted alongside it.
    *
    * A separate counter is a second source of truth: persist twenty tasks,
    * reload, and a counter reset to its initial value hands out ids that already
    * exist -- after which `toggleTask` flips two tasks and `deleteTask` removes
    * two, because both match on id. Deriving cannot drift.
    */
  def nextId(tasks: List[Task]): Int =
    tasks.map(_.id).maxOption.getOrElse(0) + 1

  // --- encode ----------------------------------------------------------

  def encode(tasks: List[Task]): String =
    JSON.stringify(
      js.Dynamic.literal(
        v = Version,
        tasks = js.Array(tasks.map(taskToJs)*)
      )
    )

  private def taskToJs(t: Task): js.Any =
    js.Dynamic.literal(
      id = t.id,
      title = t.title,
      year = t.date.year,
      month = t.date.month,
      day = t.date.day,
      hour = t.time.hour,
      minute = t.time.minute,
      priority = t.priority.toString,
      completed = t.completed
    )

  // --- decode ----------------------------------------------------------

  def decode(raw: String): Either[DecodeError, List[Task]] =
    for {
      parsed  <- parse(raw)
      version <- int(parsed, "v")
      _       <- Either.cond(version == Version, (), DecodeError.UnsupportedVersion(version))
      entries <- taskArray(parsed)
      tasks   <- traverse(entries)
    } yield tasks

  private def parse(raw: String): Either[DecodeError, js.Dynamic] =
    Try(JSON.parse(raw)).toEither.left.map(e => DecodeError.NotJson(e.getMessage))

  private def taskArray(v: js.Dynamic): Either[DecodeError, List[js.Dynamic]] = {
    val field = v.selectDynamic("tasks")
    if (js.Array.isArray(field)) Right(field.asInstanceOf[js.Array[js.Dynamic]].toList)
    else Left(DecodeError.WrongShape("expected an array at 'tasks'"))
  }

  /** Fails the whole payload on the first unreadable task rather than silently
    * dropping it -- a visitor losing one task without explanation is worse than
    * falling back to a clean slate.
    */
  private def traverse(entries: List[js.Dynamic]): Either[DecodeError, List[Task]] =
    entries.foldRight[Either[DecodeError, List[Task]]](Right(Nil)) { (entry, acc) =>
      for {
        task <- taskFromJs(entry)
        rest <- acc
      } yield task :: rest
    }

  private def taskFromJs(v: js.Dynamic): Either[DecodeError, Task] =
    for {
      id        <- int(v, "id")
      title     <- string(v, "title")
      year      <- int(v, "year")
      month     <- int(v, "month")
      day       <- int(v, "day")
      hour      <- int(v, "hour")
      minute    <- int(v, "minute")
      priority  <- priorityOf(v)
      completed <- boolean(v, "completed")
    } yield Task(id, title, TaskDate(year, month, day), TaskTime(hour, minute), priority, completed)

  // A missing field reads as `undefined` rather than throwing, so every
  // accessor checks the runtime type before trusting it.

  private def int(v: js.Dynamic, name: String): Either[DecodeError, Int] = {
    val field = v.selectDynamic(name)
    if (js.typeOf(field) == "number") Right(field.asInstanceOf[Double].toInt)
    else Left(DecodeError.WrongShape(s"expected a number at '$name'"))
  }

  private def string(v: js.Dynamic, name: String): Either[DecodeError, String] = {
    val field = v.selectDynamic(name)
    if (js.typeOf(field) == "string") Right(field.asInstanceOf[String])
    else Left(DecodeError.WrongShape(s"expected a string at '$name'"))
  }

  private def boolean(v: js.Dynamic, name: String): Either[DecodeError, Boolean] = {
    val field = v.selectDynamic(name)
    if (js.typeOf(field) == "boolean") Right(field.asInstanceOf[Boolean])
    else Left(DecodeError.WrongShape(s"expected a boolean at '$name'"))
  }

  private def priorityOf(v: js.Dynamic): Either[DecodeError, Priority] =
    string(v, "priority").flatMap { name =>
      Priority.values
        .find(_.toString == name)
        .toRight(DecodeError.WrongShape(s"unknown priority '$name'"))
    }

  // --- storage decisions, kept pure so they can be tested without a browser --

  /** What a raw stored value means. `None` is nothing stored at all. */
  def interpretLoad(raw: Option[String]): LoadResult =
    raw match {
      case None => LoadResult.Empty
      case Some(text) =>
        decode(text) match {
          case Right(tasks) => LoadResult.Loaded(tasks)
          case Left(error)  => LoadResult.Unreadable(error)
        }
    }

  /** What an incoming `storage` event from another tab means for this one.
    *
    * `None` means ignore it. The equality check is not an optimisation, it is
    * what stops an endless loop: adopting a list makes this tab write it back,
    * which raises a `storage` event in the tab that sent it, which would adopt
    * and write again forever. Comparing first means the second tab sees a value
    * it already holds and stops.
    */
  def interpretExternalChange(
      key: String,
      newValue: Option[String],
      current: List[Task]
  ): Option[List[Task]] =
    if (key != StorageKey) None
    else newValue.flatMap(decode(_).toOption).filter(_ != current)
}
