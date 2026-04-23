package taskmanager

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

// 1. Case Classes
case class Task(id: Int, title: String, completed: Boolean)

// ADT for Filters
enum FilterMode:
  case All, Pending, Completed

object Main {
  
  // State variables - using Laminar's Var to hold immutable state references
  private val tasksVar = Var(List.empty[Task])
  private val filterModeVar = Var(FilterMode.All)
  private val nextIdVar = Var(1)

  // 3. Pure Functions (State Transformations returning new lists - Immutable State)
  
  def addTask(tasks: List[Task], title: String, id: Int): List[Task] =
    tasks :+ Task(id, title.trim, completed = false)

  def toggleTask(tasks: List[Task], id: Int): List[Task] =
    // 4. Higher-Order Functions (map)
    tasks.map { task => 
      if (task.id == id) task.copy(completed = !task.completed) else task
    }

  def deleteTask(tasks: List[Task], id: Int): List[Task] =
    // 4. Higher-Order Functions (filterNot)
    tasks.filterNot(_.id == id) 

  def clearCompleted(tasks: List[Task]): List[Task] =
    // 4. Higher-Order Functions (filterNot)
    tasks.filterNot(_.completed) 

  def filterTasks(tasks: List[Task], mode: FilterMode): List[Task] =
    // 5. Pattern Matching & Higher-Order Functions (filter)
    mode match 
      case FilterMode.All       => tasks
      case FilterMode.Pending   => tasks.filterNot(_.completed)
      case FilterMode.Completed => tasks.filter(_.completed)

  // Explicitly utilizing 'find' for a lookup (satisfying requirement)
  def findTask(tasks: List[Task], id: Int): Option[Task] = 
    tasks.find(_.id == id)

  // Explicitly utilizing 'foldLeft' for statistics calculation (satisfying requirement)
  // Returns (Total, Pending, Completed)
  def calculateStats(tasks: List[Task]): (Int, Int, Int) =
    tasks.foldLeft((0, 0, 0)) { case ((total, pending, completed), task) =>
      val newPending = if (!task.completed) pending + 1 else pending
      val newCompleted = if (task.completed) completed + 1 else completed
      (total + 1, newPending, newCompleted)
    }

  def main(args: Array[String]): Unit = {
    dom.document.addEventListener("DOMContentLoaded", { (_: dom.Event) =>
      val appContainer = dom.document.getElementById("app")
      render(appContainer, appElement())
    })
  }

  def appElement(): HtmlElement = {
    // 2. Immutable State (Signals derived from Vars)
    val tasksSignal = tasksVar.signal
    val filterModeSignal = filterModeVar.signal
    
    val filteredTasksSignal = tasksSignal.combineWith(filterModeSignal).map {
      case (tasks, mode) => filterTasks(tasks, mode)
    }

    val statsSignal = tasksSignal.map(calculateStats)
    val totalTasksSignal = statsSignal.map(_._1)
    val pendingTasksSignal = statsSignal.map(_._2)
    val completedTasksSignal = statsSignal.map(_._3)

    val titleInputVar = Var("")

    div(
      h1("Functional Task Manager"),
      
      // Input Group
      div(cls := "input-group",
        input(
          typ := "text",
          placeholder := "Add a new task...",
          controlled(
            value <-- titleInputVar,
            onInput.mapToValue --> titleInputVar
          ),
          onKeyDown.filter(_.key == "Enter").mapToValue.filter(_.trim.nonEmpty) --> { title =>
            val id = nextIdVar.now()
            tasksVar.update(tasks => addTask(tasks, title, id))
            nextIdVar.update(_ + 1)
            titleInputVar.set("")
          }
        ),
        button(
          "Add",
          onClick.mapTo(titleInputVar.now()).filter(_.trim.nonEmpty) --> { title =>
             val id = nextIdVar.now()
             tasksVar.update(tasks => addTask(tasks, title, id))
             nextIdVar.update(_ + 1)
             titleInputVar.set("")
          }
        )
      ),

      // Filters
      div(cls := "filters",
        button(
          "All",
          cls.toggle("active") <-- filterModeSignal.map(_ == FilterMode.All),
          onClick --> { _ => filterModeVar.set(FilterMode.All) }
        ),
        button(
          "Pending",
          cls.toggle("active") <-- filterModeSignal.map(_ == FilterMode.Pending),
          onClick --> { _ => filterModeVar.set(FilterMode.Pending) }
        ),
        button(
          "Completed",
          cls.toggle("active") <-- filterModeSignal.map(_ == FilterMode.Completed),
          onClick --> { _ => filterModeVar.set(FilterMode.Completed) }
        )
      ),

      // Task List
      ul(cls := "task-list",
        children <-- filteredTasksSignal.split(_.id)(renderTask)
      ),

      // Stats
      div(cls := "stats",
        span("Total: ", child.text <-- totalTasksSignal.map(_.toString)),
        span("Pending: ", child.text <-- pendingTasksSignal.map(_.toString)),
        span("Completed: ", child.text <-- completedTasksSignal.map(_.toString))
      ),

      // Clear Completed Button
      button(
        cls := "clear-btn",
        "Clear Completed",
        onClick --> { _ => tasksVar.update(clearCompleted) }
      )
    )
  }

  def renderTask(id: Int, initialTask: Task, taskSignal: Signal[Task]): HtmlElement = {
    li(cls := "task-item",
      cls.toggle("completed") <-- taskSignal.map(_.completed),
      div(cls := "task-content",
        onClick --> { _ => tasksVar.update(tasks => toggleTask(tasks, id)) },
        input(
          typ := "checkbox",
          checked <-- taskSignal.map(_.completed),
          onChange.mapToChecked --> { _ => /* handled by parent div click */ }
        ),
        span(child.text <-- taskSignal.map(_.title))
      ),
      button(
        cls := "delete-btn",
        "Delete",
        onClick --> { _ => tasksVar.update(tasks => deleteTask(tasks, id)) }
      )
    )
  }
}
