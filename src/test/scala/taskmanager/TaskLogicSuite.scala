package taskmanager

import taskmanager.Main.*

/** Covers the pure half of the application: every `List[Task] => List[Task]`
  * transformation and every derivation the dashboard reads.
  *
  * These functions are testable with no test double and no DOM, which is the
  * point the README makes about the architecture -- so the suite is also the
  * evidence for it. Nothing here touches Laminar rendering.
  */
class TaskLogicSuite extends munit.FunSuite {

  // `TaskDate.month` is 0-based, matching the JS Date it is built from, so
  // month 8 is September. Getting this wrong is the easiest bug in the file.
  private val yesterday = TaskDate(2026, 8, 14)
  private val today     = TaskDate(2026, 8, 15)
  private val tomorrow  = TaskDate(2026, 8, 16)

  private def task(
      id: Int,
      title: String = "Task",
      date: TaskDate = TaskDate(2026, 8, 15),
      time: TaskTime = TaskTime(9, 0),
      priority: Priority = Priority.Medium,
      completed: Boolean = false
  ): Task = Task(id, title, date, time, priority, completed)

  // --- addTask ---------------------------------------------------------

  test("addTask appends without touching the original list") {
    val before = List(task(1))
    val after  = addTask(before, "Write tests", today, TaskTime(10, 30), Priority.High, 2)

    assertEquals(after.length, 2)
    assertEquals(before.length, 1, "the input list must not be mutated")
    assertEquals(before, List(task(1)))
  }

  test("addTask trims the title and starts the task incomplete") {
    val added = addTask(Nil, "  Buy milk  ", today, TaskTime(9, 0), Priority.Low, 7).head

    assertEquals(added.title, "Buy milk")
    assertEquals(added.completed, false)
    assertEquals(added.id, 7)
    assertEquals(added.priority, Priority.Low)
  }

  test("addTask on an empty list produces a single task") {
    assertEquals(addTask(Nil, "First", today, TaskTime(0, 0), Priority.Medium, 1).length, 1)
  }

  // --- toggleTask ------------------------------------------------------

  test("toggleTask flips only the matching task") {
    val before = List(task(1), task(2, completed = true), task(3))
    val after  = toggleTask(before, 2)

    assertEquals(after.map(_.completed), List(false, false, false))
    assertEquals(before.map(_.completed), List(false, true, false), "input must be unchanged")
  }

  test("toggleTask flips an incomplete task to complete") {
    assertEquals(toggleTask(List(task(1)), 1).head.completed, true)
  }

  test("toggleTask leaves the list alone for an unknown id") {
    val before = List(task(1), task(2))
    assertEquals(toggleTask(before, 99), before)
  }

  // --- deleteTask ------------------------------------------------------

  test("deleteTask removes only the matching task") {
    val before = List(task(1), task(2), task(3))
    val after  = deleteTask(before, 2)

    assertEquals(after.map(_.id), List(1, 3))
    assertEquals(before.length, 3, "input must be unchanged")
  }

  test("deleteTask is a no-op for an unknown id") {
    val before = List(task(1))
    assertEquals(deleteTask(before, 99), before)
  }

  test("deleteTask on an empty list stays empty") {
    assertEquals(deleteTask(Nil, 1), List.empty[Task])
  }

  // --- sortTasks -------------------------------------------------------

  test("sortTasks orders by date, then by time within a day") {
    val unsorted = List(
      task(1, date = tomorrow, time = TaskTime(8, 0)),
      task(2, date = today, time = TaskTime(17, 0)),
      task(3, date = today, time = TaskTime(9, 30)),
      task(4, date = yesterday, time = TaskTime(23, 0))
    )

    assertEquals(sortTasks(unsorted).map(_.id), List(4, 3, 2, 1))
    assertEquals(unsorted.map(_.id), List(1, 2, 3, 4), "input must be unchanged")
  }

  test("sortTasks handles an empty list") {
    assertEquals(sortTasks(Nil), List.empty[Task])
  }

  // --- filterTasksForView ----------------------------------------------

  private val mixed = List(
    task(1, date = today, priority = Priority.High),
    task(2, date = today, priority = Priority.Medium, completed = true),
    task(3, date = tomorrow, priority = Priority.Low),
    task(4, date = tomorrow, priority = Priority.High, completed = true)
  )

  test("the Tasks view shows everything, completed included, in sorted order") {
    assertEquals(filterTasksForView(mixed, SidebarView.Tasks).map(_.id), List(1, 2, 3, 4))
  }

  test("the Pending view excludes completed tasks") {
    assertEquals(filterTasksForView(mixed, SidebarView.Pending).map(_.id), List(1, 3))
  }

  test("the Completed view keeps only completed tasks") {
    assertEquals(filterTasksForView(mixed, SidebarView.Completed).map(_.id), List(2, 4))
  }

  test("priority views show outstanding work only, never completed tasks") {
    // task 4 is High but done, so a High Priority view must not surface it.
    assertEquals(filterTasksForView(mixed, SidebarView.HighPriority).map(_.id), List(1))
    assertEquals(filterTasksForView(mixed, SidebarView.MediumPriority).map(_.id), List.empty[Int])
    assertEquals(filterTasksForView(mixed, SidebarView.LowPriority).map(_.id), List(3))
  }

  // --- isOverdue -------------------------------------------------------

  test("an unfinished task dated before today is overdue") {
    assert(isOverdue(task(1, date = yesterday), today))
  }

  test("a completed task is never overdue, however old") {
    assert(!isOverdue(task(1, date = yesterday, completed = true), today))
  }

  test("a task dated today or later is not overdue") {
    assert(!isOverdue(task(1, date = today), today))
    assert(!isOverdue(task(2, date = tomorrow), today))
  }

  // --- searchTasks -----------------------------------------------------

  private val searchable = List(
    task(1, title = "Buy milk"),
    task(2, title = "Call the DENTIST"),
    task(3, title = "Write tests")
  )

  test("an empty or whitespace-only query returns everything") {
    assertEquals(searchTasks(searchable, ""), searchable)
    assertEquals(searchTasks(searchable, "   "), searchable)
  }

  test("search matches a substring regardless of case") {
    assertEquals(searchTasks(searchable, "dentist").map(_.id), List(2))
    assertEquals(searchTasks(searchable, "MILK").map(_.id), List(1))
    assertEquals(searchTasks(searchable, "t").map(_.id), List(2, 3))
  }

  test("search trims the query before matching") {
    assertEquals(searchTasks(searchable, "  milk  ").map(_.id), List(1))
  }

  test("a query matching nothing returns an empty list") {
    assertEquals(searchTasks(searchable, "zzz"), List.empty[Task])
  }

  // --- summarise / countByPriority -------------------------------------

  test("summarise counts totals, pending, completed and overdue") {
    val tasks = List(
      task(1, date = yesterday),                    // pending + overdue
      task(2, date = today, completed = true),      // completed
      task(3, date = tomorrow),                     // pending
      task(4, date = yesterday, completed = true)   // completed, not overdue
    )

    assertEquals(summarise(tasks, today), Summary(total = 4, pending = 2, completed = 2, overdue = 1))
  }

  test("summarise on an empty list is all zeroes") {
    assertEquals(summarise(Nil, today), Summary(0, 0, 0, 0))
  }

  test("percentComplete guards against dividing by zero") {
    assertEquals(Summary(0, 0, 0, 0).percentComplete, 0)
  }

  test("percentComplete truncates rather than rounding") {
    assertEquals(Summary(total = 3, pending = 2, completed = 1, overdue = 0).percentComplete, 33)
    assertEquals(Summary(total = 4, pending = 2, completed = 2, overdue = 0).percentComplete, 50)
    assertEquals(Summary(total = 2, pending = 0, completed = 2, overdue = 0).percentComplete, 100)
  }

  test("countByPriority ignores completed tasks") {
    val tasks = List(
      task(1, priority = Priority.High),
      task(2, priority = Priority.High, completed = true),
      task(3, priority = Priority.Low)
    )

    assertEquals(countByPriority(tasks, Priority.High), 1)
    assertEquals(countByPriority(tasks, Priority.Medium), 0)
    assertEquals(countByPriority(tasks, Priority.Low), 1)
  }

  // --- groupByDay ------------------------------------------------------

  test("groupByDay collects a day's tasks into one group, in order") {
    val tasks = List(
      task(1, date = today, time = TaskTime(9, 0)),
      task(2, date = today, time = TaskTime(14, 0)),
      task(3, date = tomorrow, time = TaskTime(8, 0))
    )
    val groups = groupByDay(tasks)

    assertEquals(groups.length, 2)
    assertEquals(groups.map(_._1), List(today, tomorrow))
    assertEquals(groups.head._2.map(_.id), List(1, 2))
    assertEquals(groups.last._2.map(_.id), List(3))
  }

  test("groupByDay returns nothing for an empty list") {
    assertEquals(groupByDay(Nil), List.empty[(TaskDate, List[Task])])
  }

  test("groupByDay merges consecutive days only, so it expects sorted input") {
    // Documents a real precondition: the fold compares each task against the
    // group it is currently building, not against every group seen so far. On
    // unsorted input the same day therefore opens a second group. Callers pass
    // the output of sortTasks, which is why this is safe in the app.
    val unsorted = List(
      task(1, date = today),
      task(2, date = tomorrow),
      task(3, date = today)
    )

    assertEquals(groupByDay(unsorted).map(_._1), List(today, tomorrow, today))
  }

  // --- dayLabel --------------------------------------------------------

  test("dayLabel names today and tomorrow in words") {
    assertEquals(dayLabel(today, today, tomorrow), "Today")
    assertEquals(dayLabel(tomorrow, today, tomorrow), "Tomorrow")
  }

  test("dayLabel spells out an overdue day rather than relying on colour") {
    assertEquals(dayLabel(yesterday, today, tomorrow), "Overdue · 14/9/2026")
  }

  test("dayLabel falls back to the plain date for any other day") {
    assertEquals(dayLabel(TaskDate(2026, 8, 20), today, tomorrow), "20/9/2026")
  }

  // --- date and time formatting ----------------------------------------

  test("ordinal orders dates across day, month and year boundaries") {
    assert(TaskDate(2026, 8, 14).ordinal < TaskDate(2026, 8, 15).ordinal)
    assert(TaskDate(2026, 7, 31).ordinal < TaskDate(2026, 8, 1).ordinal)
    assert(TaskDate(2025, 11, 31).ordinal < TaskDate(2026, 0, 1).ordinal)
  }

  test("toIsoString zero-pads and shifts the month into human numbering") {
    assertEquals(TaskDate(2026, 0, 5).toIsoString, "2026-01-05")
    assertEquals(TaskDate(2026, 11, 31).toIsoString, "2026-12-31")
  }

  test("toDisplayString reads as a plain day/month/year") {
    assertEquals(TaskDate(2026, 0, 5).toDisplayString, "5/1/2026")
  }

  test("TaskTime zero-pads to a 24-hour clock") {
    assertEquals(TaskTime(9, 5).toDisplayString, "09:05")
    assertEquals(TaskTime(0, 0).toDisplayString, "00:00")
    assertEquals(TaskTime(23, 59).toDisplayString, "23:59")
  }

  // --- view copy -------------------------------------------------------

  test("every view has a name") {
    assertEquals(viewName(SidebarView.Tasks), "All Tasks")
    assertEquals(viewName(SidebarView.Pending), "Pending Tasks")
    assertEquals(viewName(SidebarView.Completed), "Completed Tasks")
    assertEquals(viewName(SidebarView.HighPriority), "High Priority")
    assertEquals(viewName(SidebarView.MediumPriority), "Medium Priority")
    assertEquals(viewName(SidebarView.LowPriority), "Low Priority")
  }

  test("an active search explains itself instead of showing the view's empty copy") {
    val (heading, body) = emptyMessage(SidebarView.Pending, "  dentist  ")

    assertEquals(heading, "No matches")
    assert(body.contains("dentist"), s"expected the query to be quoted back, got: $body")
  }

  test("each view has its own empty-state copy when nothing is searched") {
    assertEquals(emptyMessage(SidebarView.Tasks, "")._1, "No tasks yet")
    assertEquals(emptyMessage(SidebarView.Pending, "")._1, "All clear")
    assertEquals(emptyMessage(SidebarView.Completed, "")._1, "Nothing completed yet")
    assertEquals(emptyMessage(SidebarView.HighPriority, "")._1, "No high priority tasks")
  }
}
