# Functional Task Manager (Scala.js)

### 🚀 [Live Demo: Try the Application Here](https://task-manager-using-functional-progr.vercel.app/)

A professional, frontend-only productivity web application built using Scala.js and Laminar. This project transforms a basic task list into a modern planner, explicitly demonstrating the practical application of functional programming paradigms in a reactive UI architecture.

---

## 🛠 Functional Programming Usage

This project strictly adheres to functional programming principles to manage state and business logic in a robust, predictable manner. The core architecture relies on unidirectional data flow, separating pure state transformations from UI rendering. Below are detailed examples of how these concepts are applied throughout the codebase.

### 1. Immutability & Case Classes
The application state is entirely modeled using immutable `case class` structures. There is no in-place mutation of data. When a user interacts with the application, a completely new copy of the data structure is created. 

This ensures data consistency and prevents unintended side-effects, making the system incredibly easy to reason about.

```scala
// Immutable Data Models representing the domain
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
```

### 2. Pure Functions & State Transformations
All core business logic is encapsulated in pure functions. A pure function always produces the same output for the same input and has no side effects (it doesn't modify external variables).

Functions such as `addTask`, `toggleTask`, and `deleteTask` take the current state (a `List[Task]`) and explicit parameters as input, returning a brand new, transformed state.

```scala
// --- Pure Functions (State Transformations) ---

// Returns a new list with the new task appended, leaving the old list intact
def addTask(tasks: List[Task], title: String, date: TaskDate, time: TaskTime, priority: Priority, id: Int): List[Task] =
  tasks :+ Task(id, title.trim, date, time, priority, completed = false)

// Returns a new list where the specific task's 'completed' status is flipped using .copy()
def toggleTask(tasks: List[Task], id: Int): List[Task] =
  tasks.map(t => if (t.id == id) t.copy(completed = !t.completed) else t)

// Returns a new list excluding the deleted task
def deleteTask(tasks: List[Task], id: Int): List[Task] =
  tasks.filterNot(_.id == id)
```

### 3. Higher-Order Functions
The project extensively leverages Scala's rich collections API and higher-order functions (functions that take other functions as parameters) to manipulate task lists dynamically without loops.

*   **`map`**: Used to transform the list of tasks, such as when updating a task's status, and to reactively project state signals to UI elements.
*   **`filter` / `filterNot`**: Employed to derive specific views, such as identifying completed tasks or filtering by priority levels.
*   **`sortBy`**: Applies chronological ordering to tasks based on a tuple of nested date and time properties.

```scala
// Sorting chronologically using a higher-order function (sortBy) and a tuple
def sortTasks(tasks: List[Task]): List[Task] =
  tasks.sortBy(t => (t.date.year, t.date.month, t.date.day, t.time.hour, t.time.minute))
```

### 4. Pattern Matching
Pattern matching is used to type-safely handle the algebraic data types (ADTs) representing the application's views. It allows for exhaustive, compiler-checked logic branching without using brittle `if-else` chains.

For example, the `SidebarView` enum is pattern-matched within `filterTasksForView` to deterministically apply the correct filtering logic for each section:

```scala
enum SidebarView:
  case Tasks, Pending, Completed, HighPriority, MediumPriority, LowPriority

// Pure function using pattern matching to determine which tasks to display
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
```

### 5. Functional Reactive Programming (FRP) with Laminar
The user interface is entirely driven by reactive streams (`Var` and `Signal` from Laminar). State is separated from the UI; the UI simply *subscribes* to changes in the state and re-renders automatically using functional transformations (like `.map`).

```scala
// Reactive State Declarations
private val tasksVar = Var(List( /* initial tasks */ ))
private val selectedViewVar = Var(SidebarView.Tasks)

// ... Inside the UI rendering ...
// The UI functionally reacts to changes in tasksVar and selectedViewVar
children <-- tasksVar.signal.combineWith(selectedViewVar.signal).map { case (tasks, view) => 
  val filtered = filterTasksForView(tasks, view)
  if (filtered.isEmpty) {
    List(div(cls := "empty-state", "No tasks here. Add a new task to get started!"))
  } else {
    filtered.map(renderTaskRow)
  }
}

// Stats functionally derived from the signal without manual DOM manipulation
div(cls := "stat-box", "Pending: ", strong(child.text <-- tasksVar.signal.map(_.count(!_.completed).toString)))
```

---

## ✨ Features

*   **Task Management**: Create, toggle, and delete tasks seamlessly.
*   **Detailed Properties**: Assign precise dates, times, and priority levels (High/Medium/Low) to tasks.
*   **Chronological Sorting**: Tasks automatically sort by nearest date and time dynamically.
*   **Dynamic Views**: Filter tasks by All, Pending, Completed, or specific Priority levels using the sidebar.
*   **Reactive UI**: Built entirely with Laminar for instantaneous, state-driven UI updates with zero manual DOM manipulation.
*   **Modern Design**: Clean, responsive layout with intuitive interactions and robust form handling.

---

## 🚀 Deployment (Vercel)

This project is configured for seamless deployment on Vercel. Connect the GitHub repository to Vercel, and it will automatically use `vercel.json` and `build.sh` to compile the Scala application into JavaScript and serve it from the `public` directory.
