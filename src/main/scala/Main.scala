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

  // --- Pure Functions (State Transformations) ---

  def addTask(tasks: List[Task], title: String, date: TaskDate, time: TaskTime, priority: Priority, id: Int): List[Task] =
    tasks :+ Task(id, title.trim, date, time, priority, completed = false)

  def toggleTask(tasks: List[Task], id: Int): List[Task] =
    tasks.map(t => if (t.id == id) t.copy(completed = !t.completed) else t)

  def deleteTask(tasks: List[Task], id: Int): List[Task] =
    tasks.filterNot(_.id == id)

  def sortTasks(tasks: List[Task]): List[Task] =
    tasks.sortBy(t => (t.date.year, t.date.month, t.date.day, t.time.hour, t.time.minute))

  // Pure function for grouping logic
  def groupTasksForView(tasks: List[Task], view: SidebarView): List[(String, List[Task])] = {
    val now = new Date()
    val today = TaskDate(now.getFullYear().toInt, now.getMonth().toInt, now.getDate().toInt)

    def isToday(t: Task) = t.date == today
    def isPast(t: Task) = 
      t.date.year < today.year || 
      (t.date.year == today.year && t.date.month < today.month) || 
      (t.date.year == today.year && t.date.month == today.month && t.date.day < today.day)
    def isFuture(t: Task) = !isToday(t) && !isPast(t)

    val activeTasks = sortTasks(tasks.filter(!_.completed))
    val completedTasks = sortTasks(tasks.filter(_.completed))

    val result = view match {
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
    result.filter(_._2.nonEmpty)
  }

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
          
          // Dynamic Quick Add
          renderQuickAdd(),

          // Grouped Task List with Empty State
          div(
            children <-- tasksVar.signal.combineWith(selectedViewVar.signal).map { case (tasks, view) => 
              val groups = groupTasksForView(tasks, view)
              if (groups.isEmpty) {
                List(div(cls := "empty-state", "No tasks here. Get started by adding one!"))
              } else {
                groups.map { case (title, ts) =>
                  div(
                    h3(cls := "group-title", title),
                    div(ts.map(renderTaskRow))
                  )
                }
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
    val isAddingVar = Var(false)
    val titleVar = Var("")
    val priorityVar = Var(Priority.Medium)
    
    val now = new Date()
    val todayDate = TaskDate(now.getFullYear().toInt, now.getMonth().toInt, now.getDate().toInt)
    val dateVar = Var(todayDate)
    val timeVar = Var(TaskTime(9, 0))

    div(cls := "quick-add-container",
      // Trigger
      div(
        cls := "add-task-trigger",
        display <-- isAddingVar.signal.map(if (_) "none" else "flex"),
        onClick --> { _ => isAddingVar.set(true) },
        span(cls := "add-task-icon", "+"),
        span("Add task")
      ),
      // Expanded Form
      div(
        cls := "add-task-form",
        display <-- isAddingVar.signal.map(if (_) "block" else "none"),
        input(
          typ := "text",
          cls := "quick-add-input",
          placeholder := "Task name",
          controlled(value <-- titleVar, onInput.mapToValue --> titleVar),
          onKeyDown.filter(_.key == "Enter") --> { _ => 
            // Trigger add on Enter
            dom.document.getElementById("submit-task-btn").asInstanceOf[dom.html.Button].click()
          }
        ),
        div(cls := "advanced-options",
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
        ),
        div(cls := "add-task-actions",
          button(
            cls := "btn-cancel", 
            "Cancel", 
            onClick --> { _ => 
              isAddingVar.set(false)
              titleVar.set("") 
            }
          ),
          button(
            idAttr := "submit-task-btn",
            cls := "btn-submit", 
            "Add Task", 
            disabled <-- titleVar.signal.map(_.trim.isEmpty),
            onClick --> { _ =>
              val title = titleVar.now()
              if (title.trim.nonEmpty) {
                // Fixed: Correct evaluation of all state at click time
                val currentTaskDate = if (selectedViewVar.now() == SidebarView.Today && dateVar.now() == todayDate) todayDate else dateVar.now()
                tasksVar.update(ts => addTask(ts, title, currentTaskDate, timeVar.now(), priorityVar.now(), nextIdVar.now()))
                nextIdVar.update(_ + 1)
                titleVar.set("")
                isAddingVar.set(false)
              }
            }
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
