# Productivity Planner in Scala.js

This is a professional, frontend-only productivity web application built using **Scala.js** and **Laminar**. It transforms a simple task list into a modern planner with advanced sorting, filtering, and searching capabilities, all while strictly adhering to functional programming principles.

## Functional Programming Usage

The centerpiece of this project is its rigorous application of Scala's functional programming features. By leveraging the **Laminar** reactive UI library, the application maintains a unidirectional data flow that is both predictable and robust.

### 1. Immutability & Case Classes
The entire state of the application is modeled using immutable **Case Classes**.
```scala
case class Task(id: Int, title: String, date: TaskDate, time: TaskTime, priority: Priority, completed: Boolean)
```
Every interaction with the state results in a new instance of the data model rather than a mutation. This ensures that the application state is always consistent and easy to reason about.

### 2. Pure Functions
All business logic is encapsulated in **Pure Functions**. These functions take the current state and parameters as input and return a new state without any side effects.
*   `addTask`: Generates a new task list with the added entry.
*   `toggleTask`: Returns a list with the completion status of a specific task flipped.
*   `filterTasks` & `searchTasks`: Derive view-specific lists based on criteria.
*   `sortTasks`: Chronologically orders tasks by date and time.

### 3. Higher-Order Functions
The application relies heavily on standard Scala collection transformations:
*   **`map`**: Used to transform the task list and project signals for the UI.
*   **`filter`**: Used for the robust search and filtering system.
*   **`foldLeft`**: Specifically used in `calculateStats` to derive Total, Pending, and Completed counts in a single pass over the data.
*   **`partition`**: Used to separate completed and active tasks for section grouping.

### 4. Pattern Matching
Used extensively to handle logic switching in a type-safe manner, particularly for the **FilterMode** ADT and for grouping tasks into dynamic sections ("Today", "Upcoming", "Completed").

## Features

*   **Advanced Task Creation**: Support for Title, Priority, Date, and Time.
*   **Dual-Input Date System**: Sync between a visual Calendar Picker and manual dropdown selectors.
*   **Chronological Sorting**: Tasks are automatically ordered by their proximity in time.
*   **Dynamic Grouping**: Tasks are categorized into Today, Upcoming, and Completed sections.
*   **Live Search & Filters**: Instant title search and filtering by status or High Priority.
*   **Responsive Modern UI**: A clean, card-based layout inspired by Todoist and Notion.

## Local Run

To build and run the project locally:

```bash
# 1. Compile the Scala.js code
sbt fastLinkJS

# 2. Copy the compiled JS to the public directory
cp target/scala-3.3.3/functional-task-manager-fastopt/main.js public/

# 3. Open the application
open public/index.html
```

## Deploy to Vercel

This repository is optimized for **Vercel**.
1. Push the code to GitHub.
2. Import the repository into Vercel.
3. Vercel will automatically detect the `vercel.json` and use `build.sh` to install Scala/sbt and generate the production bundle.

## Screenshots Placeholder

![Main Planner View](placeholder-planner-view.png)
![Task Creation Form](placeholder-form-view.png)
