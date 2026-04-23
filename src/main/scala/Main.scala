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
  case Tasks, Pending, Completed, HighPriority, MediumPriority, LowPriority

object Main {
  
  private val now = new Date()
  private val todayDate = TaskDate(now.getFullYear().toInt, now.getMonth().toInt, now.getDate().toInt)

  private val tasksVar = Var(List(
    Task(1, "Welcome to your Planner! Click me to complete", todayDate, TaskTime(9, 0), Priority.High, false),
    Task(2, "Add your first task using the button above", TaskDate(todayDate.year, todayDate.month, todayDate.day + 1), TaskTime(10, 0), Priority.Medium, false)
  ))
  
  private val selectedViewVar = Var(SidebarView.Tasks)
  private val nextIdVar = Var(3)

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
      case SidebarView.Tasks | SidebarView.Pending => sorted.filter(!_.completed)
      case SidebarView.Completed => sorted.filter(_.completed)
      case SidebarView.HighPriority => sorted.filter(t => !t.completed && t.priority == Priority.High)
      case SidebarView.MediumPriority => sorted.filter(t => !t.completed && t.priority == Priority.Medium)
      case SidebarView.LowPriority => sorted.filter(t => !t.completed && t.priority == Priority.Low)
    }
  }

  def main(args: Array[String]): Unit = {
    dom.document.addEventListener("DOMContentLoaded", { (_: dom.Event) =>
      val appContainer = dom.document.getElementById("app")
      render(appContainer, appElement())
    })
  }

  def appElement(): HtmlElement = {
    div(cls := "app-container",
      div(cls := "sidebar",
        renderSidebarItem("Tasks", SidebarView.Tasks),
        renderSidebarItem("Pending", SidebarView.Pending),
        renderSidebarItem("Completed", SidebarView.Completed),
        div(styleAttr := "margin-top: 20px; padding: 0 12px; font-size: 0.75rem; font-weight: 700; color: #9ca3af; text-transform: uppercase;", "Priority"),
        renderSidebarItem("High Priority", SidebarView.HighPriority),
        renderSidebarItem("Medium Priority", SidebarView.MediumPriority),
        renderSidebarItem("Low Priority", SidebarView.LowPriority)
      ),
      div(cls := "main-content",
        div(cls := "content-inner",
          h1(cls := "view-title", child.text <-- selectedViewVar.signal.map {
            case SidebarView.Tasks => "All Tasks"
            case SidebarView.Pending => "Pending Tasks"
            case SidebarView.Completed => "Completed Tasks"
            case SidebarView.HighPriority => "High Priority"
            case SidebarView.MediumPriority => "Medium Priority"
            case SidebarView.LowPriority => "Low Priority"
          }),
          
          renderQuickAdd(),

          div(
            h3(cls := "group-title", "Task List"),
            children <-- tasksVar.signal.combineWith(selectedViewVar.signal).map { case (tasks, view) => 
              val filtered = filterTasksForView(tasks, view)
              if (filtered.isEmpty) {
                List(div(cls := "empty-state", "No tasks here. Add a new task to get started!"))
              } else {
                filtered.map(renderTaskRow)
              }
            }
          ),

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
        val id = nextIdVar.now()

        tasksVar.update(ts => addTask(ts, title, date, time, priority, id))
        nextIdVar.update(_ + 1)
        
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
    div(
      cls := "task-row",
      cls.toggle("completed") := task.completed,
      styleAttr := "cursor: pointer;",
      onClick --> { _ => tasksVar.update(ts => toggleTask(ts, task.id)) },
      
      div(cls := "task-checkbox-wrapper",
        input(
          typ := "checkbox",
          cls := "task-checkbox",
          checked := task.completed,
          onClick.stopPropagation --> { _ => tasksVar.update(ts => toggleTask(ts, task.id)) }
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
          typ := "button",
          cls := "delete-action",
          "Delete",
          onClick.stopPropagation --> { _ => tasksVar.update(ts => deleteTask(ts, task.id)) }
        )
      )
    )
  }
}
