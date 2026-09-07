package taskmanager

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.Date

// 1. Case Classes (Immutable Data Models)
case class TaskDate(year: Int, month: Int, day: Int) {
  def toDisplayString: String = s"$day/${month + 1}/$year"
  def toIsoString: String = f"$year%04d-${month + 1}%02d-$day%02d"
  // Comparable key for ordering and same-day equality, as a pure projection
  def ordinal: Int = year * 10000 + month * 100 + day
}

case class TaskTime(hour: Int, minute: Int) {
  def toDisplayString: String = f"$hour%02d:$minute%02d"
}

enum Priority:
  case High, Medium, Low

case class Task(
  id: Int,
  title: String,
  date: TaskDate,
  time: TaskTime,
  priority: Priority,
  completed: Boolean
)

enum SidebarView:
  case Tasks, Pending, Completed, HighPriority, MediumPriority, LowPriority

// A derived view of the task list — computed on demand, never stored.
case class Summary(total: Int, pending: Int, completed: Int, overdue: Int) {
  def percentComplete: Int = if (total == 0) 0 else (completed * 100) / total
}

object Main {

  private val now = new Date()
  private val todayDate = TaskDate(now.getFullYear().toInt, now.getMonth().toInt, now.getDate().toInt)
  private val tomorrowJs = new Date(now.getTime() + 86400000.0)
  private val tomorrowDate = TaskDate(tomorrowJs.getFullYear().toInt, tomorrowJs.getMonth().toInt, tomorrowJs.getDate().toInt)

  // Shown on a first visit only. Once anything has been stored, that wins --
  // including an empty list, which is a visitor who deleted everything rather
  // than a visitor who has never been here.
  private val seedTasks = List(
    Task(1, "Welcome to your Planner! Click me to complete", todayDate, TaskTime(9, 0), Priority.High, false),
    Task(2, "Add your first task using the button above", tomorrowDate, TaskTime(10, 0), Priority.Medium, false)
  )

  private val loaded = TaskStorage.load()

  private val tasksVar = Var(loaded match {
    case LoadResult.Loaded(tasks) => tasks
    case LoadResult.Empty         => seedTasks
    case LoadResult.Unreadable(_) => seedTasks
  })

  // Set once, at startup. A failure the visitor is never told about is the same
  // as a failure that lost their data.
  private val unreadableVar = Var(loaded match {
    case LoadResult.Unreadable(_) => true
    case _                        => false
  })

  private val selectedViewVar = Var(SidebarView.Tasks)
  private val queryVar = Var("")

  def addTask(tasks: List[Task], title: String, date: TaskDate, time: TaskTime, priority: Priority, id: Int): List[Task] =
    tasks :+ Task(id, title.trim, date, time, priority, completed = false)

  def toggleTask(tasks: List[Task], id: Int): List[Task] =
    tasks.map(t => if (t.id == id) t.copy(completed = !t.completed) else t)

  def deleteTask(tasks: List[Task], id: Int): List[Task] =
    tasks.filterNot(_.id == id)

  def sortTasks(tasks: List[Task]): List[Task] =
    tasks.sortBy(t => (t.date.year, t.date.month, t.date.day, t.time.hour, t.time.minute))

  def filterTasksForView(tasks: List[Task], view: SidebarView): List[Task] = {
    val sorted = sortTasks(tasks)
    view match {
      case SidebarView.Tasks          => sorted // Show all tasks (completed + pending)
      case SidebarView.Pending        => sorted.filter(!_.completed)
      case SidebarView.Completed      => sorted.filter(_.completed)
      case SidebarView.HighPriority   => sorted.filter(t => !t.completed && t.priority == Priority.High)
      case SidebarView.MediumPriority => sorted.filter(t => !t.completed && t.priority == Priority.Medium)
      case SidebarView.LowPriority    => sorted.filter(t => !t.completed && t.priority == Priority.Low)
    }
  }

  // --- Pure derivations backing the dashboard ---

  // A task is overdue when it is unfinished and its day has already passed.
  def isOverdue(task: Task, today: TaskDate): Boolean =
    !task.completed && task.date.ordinal < today.ordinal

  def searchTasks(tasks: List[Task], query: String): List[Task] = {
    val q = query.trim.toLowerCase
    if (q.isEmpty) tasks else tasks.filter(_.title.toLowerCase.contains(q))
  }

  def summarise(tasks: List[Task], today: TaskDate): Summary =
    Summary(
      total     = tasks.length,
      pending   = tasks.count(!_.completed),
      completed = tasks.count(_.completed),
      overdue   = tasks.count(t => isOverdue(t, today))
    )

  def countByPriority(tasks: List[Task], priority: Priority): Int =
    tasks.count(t => !t.completed && t.priority == priority)

  // Fold a sorted list into consecutive runs sharing a calendar day.
  def groupByDay(tasks: List[Task]): List[(TaskDate, List[Task])] =
    tasks.foldLeft(List.empty[(TaskDate, List[Task])]) { (acc, task) =>
      acc match {
        case (date, group) :: rest if date.ordinal == task.date.ordinal =>
          (date, group :+ task) :: rest
        case _ =>
          (task.date, List(task)) :: acc
      }
    }.reverse

  def dayLabel(date: TaskDate, today: TaskDate, tomorrow: TaskDate): String =
    if (date.ordinal == today.ordinal) "Today"
    else if (date.ordinal == tomorrow.ordinal) "Tomorrow"
    // Say it in words: the red heading alone would encode this by colour only.
    else if (date.ordinal < today.ordinal) s"Overdue · ${date.toDisplayString}"
    else date.toDisplayString

  def viewName(view: SidebarView): String = view match {
    case SidebarView.Tasks => "All Tasks"
    case SidebarView.Pending => "Pending Tasks"
    case SidebarView.Completed => "Completed Tasks"
    case SidebarView.HighPriority => "High Priority"
    case SidebarView.MediumPriority => "Medium Priority"
    case SidebarView.LowPriority => "Low Priority"
  }

  def emptyMessage(view: SidebarView, query: String): (String, String) =
    if (query.trim.nonEmpty) ("No matches", s"""Nothing here matches "${query.trim}".""")
    else view match {
      case SidebarView.Tasks          => ("No tasks yet", "Add your first task to get started.")
      case SidebarView.Pending        => ("All clear", "Nothing pending — every task is done.")
      case SidebarView.Completed      => ("Nothing completed yet", "Tasks you finish will collect here.")
      case SidebarView.HighPriority   => ("No high priority tasks", "Nothing urgent is outstanding.")
      case SidebarView.MediumPriority => ("No medium priority tasks", "Nothing outstanding at this level.")
      case SidebarView.LowPriority    => ("No low priority tasks", "Nothing outstanding at this level.")
    }

  def main(args: Array[String]): Unit = {
    // Another tab wrote the list. Adopt it, so this tab's next write extends
    // that work instead of stamping over it.
    TaskStorage.onExternalChange(() => tasksVar.now(), tasks => tasksVar.set(tasks))

    dom.document.addEventListener("DOMContentLoaded", { (_: dom.Event) =>
      val appContainer = dom.document.getElementById("app")
      render(appContainer, appElement())
    })
  }

  // Small stroked icon, feather-style.
  def icon(pathData: String): SvgElement =
    svg.svg(
      svg.cls := "icon",
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.stroke := "currentColor",
      svg.strokeWidth := "1.75",
      svg.strokeLineCap := "round",
      svg.strokeLineJoin := "round",
      svg.path(svg.d := pathData)
    )

  private val iconList   = "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"
  private val iconClock  = "M12 2.5a9.5 9.5 0 100 19 9.5 9.5 0 000-19zM12 6.5V12l3.5 2"
  private val iconCheck  = "M20.5 11.1V12a8.5 8.5 0 11-5-7.8M20.5 5L12 13.5l-2.5-2.5"
  private val iconSearch = "M11 4a7 7 0 100 14 7 7 0 000-14zM20 20l-4.2-4.2"

  def appElement(): HtmlElement = {
    val summarySignal = tasksVar.signal.map(ts => summarise(ts, todayDate))

    div(cls := "app",
      // The write edge. Signals emit their current value on subscribe, so this
      // also establishes the key on a first visit.
      tasksVar.signal --> { tasks => TaskStorage.save(tasks) },
      renderSidebar(summarySignal),
      mainTag(cls := "main",
        renderTopBar(),
        div(cls := "content",
          renderUnreadableNotice(),
          renderStatGrid(summarySignal),
          div(cls := "columns",
            div(cls := "col-main",
              renderQuickAdd(),
              renderTaskList()
            ),
            asideTag(cls := "col-rail",
              renderProgressCard(summarySignal),
              renderBreakdownCard()
            )
          )
        )
      )
    )
  }

  def renderSidebar(summarySignal: Signal[Summary]): HtmlElement =
    asideTag(cls := "sidebar",
      div(cls := "brand",
        div(cls := "brand-mark", "λ"),
        div(cls := "brand-text",
          div(cls := "brand-name", "Planner"),
          div(cls := "brand-sub", "Functional task manager")
        )
      ),
      navTag(cls := "nav",
        div(cls := "nav-label", "Views"),
        renderSidebarItem("Tasks", SidebarView.Tasks, iconList, summarySignal.map(_.total)),
        renderSidebarItem("Pending", SidebarView.Pending, iconClock, summarySignal.map(_.pending)),
        renderSidebarItem("Completed", SidebarView.Completed, iconCheck, summarySignal.map(_.completed)),
        div(cls := "nav-label", "Priority"),
        renderPriorityItem("High Priority", SidebarView.HighPriority, "high", Priority.High),
        renderPriorityItem("Medium Priority", SidebarView.MediumPriority, "medium", Priority.Medium),
        renderPriorityItem("Low Priority", SidebarView.LowPriority, "low", Priority.Low)
      )
    )

  // Shared interaction contract for every navigation item.
  def navBindings(view: SidebarView): Seq[Modifier[HtmlElement]] = Seq(
    cls.toggle("active") <-- selectedViewVar.signal.map(_ == view),
    role := "button",
    tabIndex := 0,
    aria.current <-- selectedViewVar.signal.map(v => if (v == view) "true" else "false"),
    onClick --> { _ => selectedViewVar.set(view) },
    onKeyDown.filter(e => e.key == "Enter" || e.key == " ").preventDefault --> { _ =>
      selectedViewVar.set(view)
    }
  )

  def renderSidebarItem(label: String, view: SidebarView, glyph: String, count: Signal[Int]): HtmlElement =
    div(
      cls := "nav-item",
      navBindings(view),
      span(cls := "nav-icon", icon(glyph)),
      span(cls := "nav-text", label),
      span(cls := "nav-count", child.text <-- count.map(_.toString))
    )

  def renderPriorityItem(label: String, view: SidebarView, tone: String, priority: Priority): HtmlElement =
    div(
      cls := "nav-item",
      navBindings(view),
      span(cls := s"nav-dot dot-$tone"),
      span(cls := "nav-text", label),
      span(cls := "nav-count",
        child.text <-- tasksVar.signal.map(ts => countByPriority(ts, priority).toString))
    )

  def renderTopBar(): HtmlElement = {
    val visibleCount = Signal.combine(tasksVar.signal, selectedViewVar.signal, queryVar.signal)
      .map { case (tasks, view, q) => searchTasks(filterTasksForView(tasks, view), q).length }

    headerTag(cls := "topbar",
      div(cls := "topbar-heading",
        h1(cls := "view-title", child.text <-- selectedViewVar.signal.map(viewName)),
        p(cls := "view-sub",
          span(child.text <-- visibleCount.map(n => if (n == 1) "1 task" else s"$n tasks")),
          span(cls := "dot-sep", "·"),
          span(todayDate.toDisplayString)
        )
      ),
      div(cls := "search",
        span(cls := "search-icon", icon(iconSearch)),
        input(
          typ := "search",
          cls := "search-input",
          placeholder := "Search tasks",
          aria.label := "Search tasks",
          value <-- queryVar,
          onInput.mapToValue --> queryVar
        ),
        button(
          typ := "button",
          cls := "search-clear",
          aria.label := "Clear search",
          display <-- queryVar.signal.map(q => if (q.isEmpty) "none" else "flex"),
          onClick --> { _ => queryVar.set("") },
          "×"
        )
      )
    )
  }

  /** Shown when startup found a payload it could not decode. The tasks are not
    * lost -- they were moved aside before the seed list could overwrite them --
    * so the notice says where they went rather than just apologising.
    */
  def renderUnreadableNotice(): HtmlElement =
    div(
      cls := "notice",
      display <-- unreadableVar.signal.map(if (_) "flex" else "none"),
      role := "status",
      div(cls := "notice-body",
        div(cls := "notice-title", "Saved tasks could not be read"),
        p(cls := "notice-text",
          s"They were kept under the browser storage key ${TaskStorage.QuarantineKey} " +
            "and this list started fresh. Nothing was deleted."
        )
      ),
      button(
        typ := "button",
        cls := "notice-dismiss",
        aria.label := "Dismiss",
        onClick --> { _ => unreadableVar.set(false) },
        "×"
      )
    )

  def renderStatGrid(summarySignal: Signal[Summary]): HtmlElement =
    sectionTag(cls := "stat-grid",
      renderStat("Total", summarySignal.map(_.total.toString), "neutral"),
      renderStat("Pending", summarySignal.map(_.pending.toString), "accent"),
      renderStat("Completed", summarySignal.map(_.completed.toString), "done"),
      renderStat("Overdue", summarySignal.map(_.overdue.toString), "warn")
    )

  def renderStat(label: String, value: Signal[String], tone: String): HtmlElement =
    div(cls := s"stat stat-$tone",
      div(cls := "stat-value", child.text <-- value),
      div(cls := "stat-label", label)
    )

  def renderProgressCard(summarySignal: Signal[Summary]): HtmlElement =
    div(cls := "card",
      div(cls := "card-title", "Progress"),
      div(cls := "progress-figure",
        span(cls := "progress-value", child.text <-- summarySignal.map(_.percentComplete.toString)),
        span(cls := "progress-unit", "%")
      ),
      div(cls := "progress-track",
        div(cls := "progress-fill",
          styleAttr <-- summarySignal.map(s => s"width: ${s.percentComplete}%")
        )
      ),
      p(cls := "card-note",
        child.text <-- summarySignal.map { s =>
          if (s.total == 0) "Nothing tracked yet."
          else s"${s.completed} of ${s.total} complete"
        }
      )
    )

  def renderBreakdownCard(): HtmlElement = {
    def row(label: String, tone: String, priority: Priority): HtmlElement = {
      val countSignal = tasksVar.signal.map(ts => countByPriority(ts, priority))
      val maxSignal = tasksVar.signal.map { ts =>
        List(Priority.High, Priority.Medium, Priority.Low).map(p => countByPriority(ts, p)).max
      }
      div(cls := "breakdown-row",
        span(cls := s"nav-dot dot-$tone"),
        span(cls := "breakdown-label", label),
        div(cls := "breakdown-track",
          div(cls := s"breakdown-fill fill-$tone",
            styleAttr <-- countSignal.combineWith(maxSignal).map { case (c, m) =>
              val pct = if (m == 0) 0 else (c * 100) / m
              s"width: $pct%"
            }
          )
        ),
        span(cls := "breakdown-count", child.text <-- countSignal.map(_.toString))
      )
    }

    div(cls := "card",
      div(cls := "card-title", "Open by priority"),
      row("High", "high", Priority.High),
      row("Medium", "medium", Priority.Medium),
      row("Low", "low", Priority.Low)
    )
  }

  def renderTaskList(): HtmlElement =
    div(cls := "task-list",
      children <-- Signal.combine(tasksVar.signal, selectedViewVar.signal, queryVar.signal).map {
        case (tasks, view, q) =>
          val filtered = searchTasks(filterTasksForView(tasks, view), q)
          if (filtered.isEmpty) List(renderEmptyState(view, q))
          else groupByDay(filtered).flatMap { case (date, group) =>
            renderGroupHeader(date, group.length) :: group.map(renderTaskRow)
          }
      }
    )

  def renderGroupHeader(date: TaskDate, count: Int): HtmlElement =
    div(cls := "group-header",
      cls.toggle("group-overdue") := date.ordinal < todayDate.ordinal,
      span(cls := "group-name", dayLabel(date, todayDate, tomorrowDate)),
      span(cls := "group-rule"),
      span(cls := "group-count", count.toString)
    )

  def renderEmptyState(view: SidebarView, query: String): HtmlElement = {
    val (heading, body) = emptyMessage(view, query)
    div(cls := "empty-state",
      div(cls := "empty-mark", icon(iconCheck)),
      div(cls := "empty-heading", heading),
      p(cls := "empty-body", body)
    )
  }

  def renderQuickAdd(): HtmlElement = {
    val isAddingVar = Var(false)
    val titleVar = Var("")
    val priorityVar = Var(Priority.Medium)
    val dateVar = Var(todayDate)
    val timeVar = Var(TaskTime(9, 0))

    def resetForm(): Unit = {
      titleVar.set("")
      priorityVar.set(Priority.Medium)
      dateVar.set(todayDate)
      timeVar.set(TaskTime(9, 0))
      isAddingVar.set(false)
    }

    def submitNewTask(): Unit = {
      val title = titleVar.now().trim
      if (title.nonEmpty) {
        val date = dateVar.now()
        val time = timeVar.now()
        val priority = priorityVar.now()

        tasksVar.update(ts => addTask(ts, title, date, time, priority, TaskCodec.nextId(ts)))

        resetForm()
        selectedViewVar.set(SidebarView.Tasks)
      }
    }

    div(cls := "quick-add-container",
      div(
        cls := "add-task-trigger",
        display <-- isAddingVar.signal.map(if (_) "none" else "flex"),
        onClick --> { _ => isAddingVar.set(true) },
        span(cls := "add-task-icon", "+"),
        span("Add task")
      ),
      form(
        cls := "add-task-form",
        display <-- isAddingVar.signal.map(if (_) "block" else "none"),
        onSubmit.preventDefault --> { _ => submitNewTask() },
        input(
          typ := "text",
          cls := "quick-add-input",
          placeholder := "Task name",
          value <-- titleVar,
          onInput.mapToValue --> titleVar
        ),
        div(cls := "advanced-options",
          div(cls := "option-field",
            label("Date"),
            input(
              typ := "date",
              value <-- dateVar.signal.map(_.toIsoString),
              onInput.mapToValue --> { v =>
                val p = v.split("-")
                if (p.length == 3) dateVar.set(TaskDate(p(0).toInt, p(1).toInt - 1, p(2).toInt))
              }
            )
          ),
          div(cls := "option-field",
            label("Time"),
            input(
              typ := "time",
              value <-- timeVar.signal.map(_.toDisplayString),
              onInput.mapToValue --> { v =>
                val p = v.split(":")
                if (p.length == 2) timeVar.set(TaskTime(p(0).toInt, p(1).toInt))
              }
            )
          ),
          div(cls := "option-field",
            label("Priority"),
            select(
              value <-- priorityVar.signal.map(_.toString),
              onChange.mapToValue --> { v => priorityVar.set(Priority.valueOf(v)) },
              option(value := "High", "High"),
              option(value := "Medium", "Medium"),
              option(value := "Low", "Low")
            )
          )
        ),
        div(cls := "add-task-actions",
          button(
            typ := "button",
            cls := "btn-cancel",
            "Cancel",
            onClick --> { _ => resetForm() }
          ),
          button(
            typ := "submit",
            cls := "btn-submit",
            "Add Task",
            disabled <-- titleVar.signal.map(_.trim.isEmpty)
          )
        )
      )
    )
  }

  def renderTaskRow(task: Task): HtmlElement = {
    val overdue = isOverdue(task, todayDate)
    div(
      cls := s"task-row task-row--${task.priority.toString.toLowerCase}",
      cls.toggle("completed") := task.completed,
      cls.toggle("overdue") := overdue,
      onClick --> { _ => tasksVar.update(ts => toggleTask(ts, task.id)) },

      div(cls := "task-checkbox-wrapper",
        input(
          typ := "checkbox",
          cls := "task-checkbox",
          checked := task.completed,
          aria.label := s"Mark '${task.title}' complete",
          onClick.stopPropagation --> { _ => tasksVar.update(ts => toggleTask(ts, task.id)) }
        )
      ),
      div(cls := "task-body",
        div(cls := "task-title", task.title),
        div(cls := "task-meta",
          span(cls := s"priority-label priority-${task.priority.toString.toLowerCase}", task.priority.toString),
          span(cls := "task-time", s"${task.date.toDisplayString} @ ${task.time.toDisplayString}"),
          if (overdue) span(cls := "overdue-chip", "Overdue") else emptyNode
        )
      ),
      div(cls := "task-actions",
        button(
          typ := "button",
          cls := "delete-action",
          aria.label := s"Delete '${task.title}'",
          "Delete",
          onClick.stopPropagation --> { _ => tasksVar.update(ts => deleteTask(ts, task.id)) }
        )
      )
    )
  }
}
