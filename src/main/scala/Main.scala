package taskmanager

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.Date
import scala.util.Try

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

enum FilterMode:
  case All, Pending, Completed, HighPriority

object Main {
  
  // State variables (Laminar Vars holding immutable references)
  private val tasksVar = Var(List.empty[Task])
  private val filterModeVar = Var(FilterMode.All)
  private val searchQueryVar = Var("")
  private val nextIdVar = Var(1)

  // --- 3. Pure Functions (State Transformations) ---

  def addTask(tasks: List[Task], title: String, date: TaskDate, time: TaskTime, priority: Priority, id: Int): List[Task] =
    tasks :+ Task(id, title.trim, date, time, priority, completed = false)

  def toggleTask(tasks: List[Task], id: Int): List[Task] =
    tasks.map(t => if (t.id == id) t.copy(completed = !t.completed) else t)

  def deleteTask(tasks: List[Task], id: Int): List[Task] =
    tasks.filterNot(_.id == id)

  def clearCompleted(tasks: List[Task]): List[Task] =
    tasks.filterNot(_.completed)

  def searchTasks(tasks: List[Task], query: String): List[Task] =
    if (query.isEmpty) tasks
    else tasks.filter(_.title.toLowerCase.contains(query.toLowerCase))

  def filterTasks(tasks: List[Task], mode: FilterMode): List[Task] =
    mode match
      case FilterMode.All          => tasks
      case FilterMode.Pending      => tasks.filterNot(_.completed)
      case FilterMode.Completed    => tasks.filter(_.completed)
      case FilterMode.HighPriority => tasks.filter(_.priority == Priority.High)

  def sortTasks(tasks: List[Task]): List[Task] =
    tasks.sortBy(t => (t.date.year, t.date.month, t.date.day, t.time.hour, t.time.minute))

  // 4. Higher-Order Functions (foldLeft used for complex stats)
  def calculateStats(tasks: List[Task]): (Int, Int, Int) =
    tasks.foldLeft((0, 0, 0)) { case ((total, pending, completed), task) =>
      if (task.completed) (total + 1, pending, completed + 1)
      else (total + 1, pending + 1, completed)
    }

  // 5. Pattern Matching for section grouping
  def groupTasks(tasks: List[Task]): List[(String, List[Task])] = {
    val now = new Date()
    val today = TaskDate(now.getFullYear().toInt, now.getMonth().toInt, now.getDate().toInt)
    
    val (completed, active) = tasks.partition(_.completed)
    val sortedActive = sortTasks(active)

    val todayTasks = sortedActive.filter(_.date == today)
    val upcomingTasks = sortedActive.filter(t => 
      t.date.year > today.year || 
      (t.date.year == today.year && t.date.month > today.month) || 
      (t.date.year == today.year && t.date.month == today.month && t.date.day > today.day)
    )

    List(
      "Today" -> todayTasks,
      "Upcoming" -> upcomingTasks,
      "Completed" -> completed
    ).filter(_._2.nonEmpty)
  }

  def main(args: Array[String]): Unit = {
    dom.document.addEventListener("DOMContentLoaded", { (_: dom.Event) =>
      val appContainer = dom.document.getElementById("app")
      render(appContainer, appElement())
    })
  }

  def appElement(): HtmlElement = {
    val tasksSignal = tasksVar.signal
    val filterModeSignal = filterModeVar.signal
    val searchSignal = searchQueryVar.signal

    val processedTasksSignal = tasksSignal
      .combineWith(filterModeSignal, searchSignal)
      .map { case (tasks, mode, query) =>
        val filtered = filterTasks(tasks, mode)
        searchTasks(filtered, query)
      }

    val groupedTasksSignal = processedTasksSignal.map(groupTasks)
    val statsSignal = tasksSignal.map(calculateStats)

    div(cls := "container",
      h1("Productivity Planner"),
      
      // Task Form Card
      renderTaskForm(),

      // Controls (Search & Filter)
      div(cls := "controls card",
        input(
          typ := "text",
          cls := "search-input",
          placeholder := "Search tasks...",
          onInput.mapToValue --> searchQueryVar
        ),
        div(cls := "filter-bar",
          renderFilterBtn("All", FilterMode.All),
          renderFilterBtn("Pending", FilterMode.Pending),
          renderFilterBtn("Completed", FilterMode.Completed),
          renderFilterBtn("High Priority", FilterMode.HighPriority)
        )
      ),

      // Task Sections
      div(
        children <-- groupedTasksSignal.map { sections =>
          sections.map { case (title, tasks) =>
            div(
              h2(cls := "section-title", title),
              div(tasks.map(renderTaskCard))
            )
          }
        }
      ),

      // Stats
      div(cls := "stats-grid",
        renderStat("Total", statsSignal.map(_._1)),
        renderStat("Pending", statsSignal.map(_._2)),
        renderStat("Completed", statsSignal.map(_._3))
      ),

      button(
        cls := "clear-btn",
        styleAttr := "margin-top: 20px; width: 100%; padding: 12px; border-radius: 8px; border: 1px solid #e5e7eb; cursor: pointer; background: white;",
        "Clear Completed Tasks",
        onClick --> { _ => tasksVar.update(clearCompleted) }
      )
    )
  }

  def renderTaskForm(): HtmlElement = {
    val titleVar = Var("")
    val priorityVar = Var(Priority.Medium)
    
    val now = new Date()
    val dateVar = Var(TaskDate(now.getFullYear().toInt, now.getMonth().toInt, now.getDate().toInt))
    val timeVar = Var(TaskTime(9, 0))

    div(cls := "card",
      div(cls := "form-group",
        label("Task Title"),
        input(
          typ := "text",
          placeholder := "What needs to be done?",
          controlled(value <-- titleVar, onInput.mapToValue --> titleVar)
        )
      ),
      div(cls := "form-row",
        div(
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
        ),
        div(
          label("Time"),
          input(
            typ := "time",
            controlled(
              value <-- timeVar.signal.map(_.toDisplayString),
              onChange.mapToValue --> { v =>
                val parts = v.split(":")
                if (parts.length == 2) timeVar.set(TaskTime(parts(0).toInt, parts(1).toInt))
              }
            )
          )
        )
      ),
      div(cls := "form-group",
        label("Date (Calendar Picker)"),
        input(
          typ := "date",
          controlled(
            value <-- dateVar.signal.map(_.toIsoString),
            onChange.mapToValue --> { v =>
              val parts = v.split("-")
              if (parts.length == 3) dateVar.set(TaskDate(parts(0).toInt, parts(1).toInt - 1, parts(2).toInt))
            }
          )
        )
      ),
      div(cls := "form-group",
        label("Manual Date Adjustment"),
        div(cls := "date-manual-row",
          select( // Day
            controlled(
              value <-- dateVar.signal.map(_.day.toString),
              onChange.mapToValue --> { v => dateVar.update(_.copy(day = v.toInt)) }
            ),
            (1 to 31).map(d => option(value := d.toString, d.toString))
          ),
          select( // Month
            controlled(
              value <-- dateVar.signal.map(_.month.toString),
              onChange.mapToValue --> { v => dateVar.update(_.copy(month = v.toInt)) }
            ),
            List("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
              .zipWithIndex.map { case (m, i) => option(value := i.toString, m) }
          ),
          select( // Year
            controlled(
              value <-- dateVar.signal.map(_.year.toString),
              onChange.mapToValue --> { v => dateVar.update(_.copy(year = v.toInt)) }
            ),
            (2024 to 2030).map(y => option(value := y.toString, y.toString))
          )
        )
      ),
      button(
        cls := "btn-primary",
        "Add Task",
        onClick --> { _ =>
          val title = titleVar.now()
          if (title.nonEmpty) {
            tasksVar.update(tasks => addTask(tasks, title, dateVar.now(), timeVar.now(), priorityVar.now(), nextIdVar.now()))
            nextIdVar.update(_ + 1)
            titleVar.set("")
          }
        }
      )
    )
  }

  def renderFilterBtn(label: String, mode: FilterMode): HtmlElement = {
    button(
      cls := "filter-btn",
      cls.toggle("active") <-- filterModeVar.signal.map(_ == mode),
      onClick --> { _ => filterModeVar.set(mode) },
      label
    )
  }

  def renderTaskCard(task: Task): HtmlElement = {
    div(cls := "task-card",
      cls.toggle("completed") := task.completed,
      input(
        typ := "checkbox",
        cls := "task-checkbox",
        checked := task.completed,
        onInput.mapToChecked --> { _ => tasksVar.update(ts => toggleTask(ts, task.id)) }
      ),
      div(cls := "task-info",
        div(cls := "task-title", task.title),
        div(cls := "task-meta",
          span(cls := s"priority-badge priority-${task.priority.toString.toLowerCase}", task.priority.toString),
          span(s"${task.date.toDisplayString} @ ${task.time.toDisplayString}")
        )
      ),
      button(
        cls := "delete-btn",
        "Delete",
        onClick --> { _ => tasksVar.update(ts => deleteTask(ts, task.id)) }
      )
    )
  }

  def renderStat(label: String, sig: Signal[Int]): HtmlElement = {
    div(cls := "stat-item",
      div(cls := "stat-value", child.text <-- sig.map(_.toString)),
      div(cls := "stat-label", label)
    )
  }
}
