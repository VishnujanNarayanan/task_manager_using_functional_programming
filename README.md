# Functional Task Manager (Scala.js)

[Try the application here](https://task-manager-using-functional-progr.vercel.app/)

A professional, frontend-only productivity web application built using Scala.js and Laminar. This project transforms a basic task list into a modern planner, demonstrating the practical application of functional programming paradigms in a reactive UI architecture.

## Functional Programming Usage

This project strictly adheres to functional programming principles to manage state and business logic in a robust, predictable manner. The core architecture relies on unidirectional data flow, separating state transformations from UI rendering.

### Immutability & Case Classes
The application state is entirely modeled using immutable `case class` structures (e.g., `Task`, `TaskDate`, `TaskTime`). There is no in-place mutation of data. When a user interacts with the application, a new copy of the data structure is created. This ensures data consistency and prevents unintended side-effects across the reactive UI.

### Pure Functions & State Transformations
All core business logic is encapsulated in pure functions. Functions such as `addTask`, `toggleTask`, `deleteTask`, `sortTasks`, and `filterTasksForView` take the current state and explicit parameters as input, returning a new, transformed state without modifying any external variables or relying on hidden side effects. This separation makes the logic highly testable and predictable.

### Higher-Order Functions
The project extensively leverages Scala's rich collections API and higher-order functions to manipulate task lists:
*   **`map`**: Used to transform the list of tasks (e.g., when toggling completion status) and to reactively project state signals to UI elements.
*   **`filter` / `filterNot`**: Employed to derive specific views, such as identifying completed tasks, removing deleted tasks, or filtering by priority levels.
*   **`sortBy`**: Applies chronological ordering to tasks based on nested date and time properties.

### Pattern Matching
Pattern matching is used to type-safely handle the algebraic data types (ADTs) representing the application's views. The `SidebarView` enum is pattern-matched within `filterTasksForView` to deterministically apply the correct filtering logic for each section (e.g., Pending, Completed, High Priority), ensuring exhaustive checks by the compiler.

## Features

*   **Task Management**: Create, toggle, and delete tasks.
*   **Detailed Properties**: Assign dates, times, and priority levels (High/Medium/Low) to tasks.
*   **Chronological Sorting**: Tasks automatically sort by nearest date and time.
*   **Dynamic Views**: Filter tasks by All, Pending, Completed, or specific Priority levels.
*   **Reactive UI**: Built with Laminar for seamless, state-driven updates.
*   **Modern Design**: Clean, responsive layout with intuitive interactions.

## Run / Deploy

### Local Development
```bash
# Compile the Scala.js code
sbt fastLinkJS

# Copy the compiled JS to the public directory
cp target/scala-3.3.3/functional-task-manager-fastopt/main.js public/

# Open the application
open public/index.html
```

### Deployment (Vercel)
This project is configured for seamless deployment on Vercel. Connect the GitHub repository to Vercel, and it will automatically use `vercel.json` and `build.sh` to compile and serve the application from the `public` directory.
