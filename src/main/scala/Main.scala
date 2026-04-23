package taskmanager

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.Date

// 1. Case Classes (Immutable Data Models)
case class TaskDate(year: Int, month: Int, day: Int) {
  def toDisplayString: String = s"$day/${month + 1}/$year"
  def toIsoString: String = f"$year%04d-${month + 1}%02d-$day%02d"
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
  case Inbox, Today, Upcoming, Completed, HighPriority

object Main {
  
  // State variables (Laminar Vars holding immutable references)
  private val tasksVar = Var(List.empty[Task])
  private val selectedViewVar = Var(SidebarView.Today)
  private val nextIdVar = Var(1)
  private val showAdvancedVar = Var(false)

  // --- Pure Functions (State Transformations) ---

  def addTask(tasks: List[Task], title: String, date: TaskDate, time: TaskTime, priority: Priority, id: Int): List[Task] =
    tasks :+ Task(id, title.trim, date, time, priority, completed = false)

  def toggleTask(tasks: List[Task], id: Int): List[Task] =
    tasks.map(t => if (t.id == id) t.copy(completed = !t.completed) else t)

  def deleteTask(tasks: List[Task], id: Int): List[Task] =
    tasks.filterNot(_.id == id)

  def sortTasks(tasks: List[Task]): List[Task] =
    tasks.sortBy(t => (t.date.year, t.date.month, t.date.day, t.time.hour, t.time.minute))

  def calculateStats(tasks: List[Task]): (Int, Int) = {
    val pending = tasks.count(!_.completed)
    (tasks.length, pending)
  }

  // Pure function for grouping logic
  def groupTasksForView(tasks: List[Task], view: SidebarView): List[(String, List[Task])] = {
    val now = new Date()
    val today = TaskDate(now.getFullYear().toInt, now.getMonth().toInt, now.getDate().toInt)

    def isToday(d: TaskDate) = d == today
    def isPast(d: TaskDate) = 
      d.year < today.year || 
      (d.year == today.year && d.month < today.month) || 
      (d.year == today.year && d.month == today.month && d.day < today.day)
    def isFuture(d: TaskDate) = !isToday(d) && !isPast(d)

    val activeTasks = sortTasks(tasks.filter(!_.completed))
    val completedTasks = sortTasks(tasks.filter(_.completed))

    view match {
      case SidebarView.Inbox =>
        List("Overdue" -> activeTasks.filter(isPast), "Today" -> activeTasks.filter(isToday), "Upcoming" -> activeTasks.filter(isFuture))
      case SidebarView.Today =>
        List("Overdue" -> activeTasks.filter(isPast), "Today" -> activeTasks.filter(isToday))
      case SidebarView.Upcoming =>
        List("Upcoming" -> activeTasks.filter(isFuture))
      case SidebarView.Completed =>
        List("Completed" -> completedTasks)
      case SidebarView.HighPriority =>
        List("High Priority" -> activeTasks.filter(_.priority == Priority.High))
    }
  }.filter(_._2.nonEmpty)

  def main(args: Array[String]): Unit = {
    dom.document.addEventListener("DOMContentLoaded", { (_: dom.Event) =>
      val appContainer = dom.document.getElementById("app")
      render(appContainer, appElement())
    })
  }

  def appElement(): HtmlElement = {
    div(cls := "app-container",
      // Left Sidebar
      div(cls := "sidebar",
        renderSidebarItem("Inbox", SidebarView.Inbox),
        renderSidebarItem("Today", SidebarView.Today),
        renderSidebarItem("Upcoming", SidebarView.Upcoming),
        renderSidebarItem("Completed", SidebarView.Completed),
        renderSidebarItem("High Priority", SidebarView.HighPriority)
      ),
      // Main Content Area
      div(cls := "main-content",
        div(cls := "content-inner",
          h1(cls := "view-title", child.text <-- selectedViewVar.signal.map(_.toString)),
          
          // Quick Add Input
          renderQuickAdd(),

          // Grouped Task List
          div(
            children <-- tasksVar.signal.combineWith(selectedViewVar.signal).map(groupTasksForView).map { sections =>
              sections.map { case (title, ts) =>
                div(
                  h3(cls := "group-title", title),
                  div(ts.map(renderTaskRow))
                )
              }
            }
          ),

          // Stats Summary
          div(cls := "stats-summary",
            div(cls := "stat-box", "Total: ", strong(child.text <-- tasksVar.signal.map(_.length.toString))),
            div(cls := "stat-box", "Pending: ", strong(child.text <-- tasksVar.signal.map(_.count(!_.completed).toString)))
          )
        )
      )
    )
  }

  def renderSidebarItem(label: String, view: SidebarView): HtmlElement = {
    div(
      cls := "sidebar-item",
      cls.toggle("active") <-- selectedViewVar.signal.map(_ == view),
      onClick --> { _ => selectedViewVar.set(view) },
      label
    )
  }

  def renderQuickAdd(): HtmlElement = {
    val titleVar = Var("")
    val priorityVar = Var(Priority.Medium)
    
    val now = new Date()
    val todayDate = TaskDate(now.getFullYear().toInt, now.getMonth().toInt, now.getDate().toInt)
    val dateVar = Var(todayDate)
    val timeVar = Var(TaskTime(9, 0))

    div(cls := "quick-add-container",
      div(cls := "quick-add-input-wrapper",
        input(
          typ := "text",
          cls := "quick-add-input",
          placeholder := "Add a task...",
          controlled(value <-- titleVar, onInput.mapToValue --> titleVar),
          onKeyDown.filter(_.key == "Enter").mapToValue.filter(_.trim.nonEmpty) --> { title =>
             // Default to Today if in Today view
             val taskDate = if (selectedViewVar.now() == SidebarView.Today) todayDate else dateVar.now()
             tasksVar.update(ts => addTask(ts, title, taskDate, timeVar.now(), priorityVar.now(), nextIdVar.now()))
             nextIdVar.update(_ + 1)
             titleVar.set("")
          }
        ),
        button(
          cls := "advanced-toggle",
          child.text <-- showAdvancedVar.signal.map(s => if (s) "Hide details" else "Add details"),
          onClick --> { _ => showAdvancedVar.update(!_) }
        )
      ),
      div(
        cls := "advanced-options",
        display <-- showAdvancedVar.signal.map(if (_) "flex" else "none"),
        div(cls := "options-row",
          div(cls := "option-field",
            label("Date"),
            input(
              typ := "date",
              controlled(
                value <-- dateVar.signal.map(_.toIsoString),
                onChange.mapToValue --> { v =>
                  val p = v.split("-")
                  if (p.length == 3) dateVar.set(TaskDate(p(0).toInt, p(1).toInt - 1, p(2).toInt))
                }
              )
            )
          ),
          div(cls := "option-field",
            label("Time"),
            input(
              typ := "time",
              controlled(
                value <-- timeVar.signal.map(_.toDisplayString),
                onChange.mapToValue --> { v =>
                  val p = v.split(":")
                  if (p.length == 2) timeVar.set(TaskTime(p(0).toInt, p(1).toInt))
                }
              )
            )
          ),
          div(cls := "option-field",
            label("Priority"),
            select(
              controlled(
                value <-- priorityVar.signal.map(_.toString),
                onChange.mapToValue --> { v => priorityVar.set(Priority.valueOf(v)) }
              ),
              option(value := "High", "High"),
              option(value := "Medium", "Medium"),
              option(value := "Low", "Low")
            )
          )
        )
      )
    )
  }

  def renderTaskRow(task: Task): HtmlElement = {
    div(
      cls := "task-row",
      cls.toggle("completed") := task.completed,
      div(cls := "task-checkbox-wrapper",
        input(
          typ := "checkbox",
          cls := "task-checkbox",
          checked := task.completed,
          onInput.mapToChecked --> { _ => tasksVar.update(ts => toggleTask(ts, task.id)) }
        )
      ),
      div(cls := "task-body",
        div(cls := "task-title", task.title),
        div(cls := "task-meta",
          span(cls := s"priority-label priority-${task.priority.toString.toLowerCase}", task.priority.toString),
          span(s"${task.date.toDisplayString} @ ${task.time.toDisplayString}")
        )
      ),
      div(cls := "task-actions",
        button(
          cls := "delete-action",
          "Delete",
          onClick --> { _ => tasksVar.update(ts => deleteTask(ts, task.id)) }
        )
      )
    )
  }
}
