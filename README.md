# Functional Task Manager in Scala.js

## What It Does

This is a complete, frontend-only Task Manager web application built using Scala and compiled to JavaScript via Scala.js. It allows users to add tasks, mark them as complete, delete them, and filter by their status. The application functions entirely in the browser and requires no backend server.

## Features

*   **Add Tasks**: Create new tasks using the input field.
*   **Toggle Completion**: Mark tasks as complete or pending.
*   **Delete Tasks**: Remove individual tasks from the list.
*   **Filter Tasks**: View all tasks, only pending tasks, or only completed tasks.
*   **Clear Completed**: Remove all completed tasks with a single click.
*   **Statistics**: View total, pending, and completed task counts.
*   **Responsive UI**: Clean, modern, and minimal design.

## Functional Programming Concepts Used

*   **Immutability**: State is updated by returning entirely new structures rather than mutating variables in place.
*   **Pure Functions**: Functions like `addTask`, `deleteTask`, `toggleTask`, `clearCompleted`, and `filterTasks` are strictly pure.
*   **Higher-Order Functions**: The application demonstrates explicit usage of:
    *   `map`
    *   `filter` (and `filterNot`)
    *   `foldLeft` (used to compute task statistics)
    *   `find` (used to query specific tasks)
*   **Case Classes**: Used to model the immutable `Task` entity.
*   **Pattern Matching**: Used within `filterTasks` to cleanly match against the ADT filter states (`All`, `Pending`, `Completed`).

## Local Run

To build and run the project locally on your machine, execute the exact following commands:

```bash
# 1. Compile the Scala.js code
sbt fastLinkJS

# 2. Copy the compiled JS file to the public directory
cp target/scala-3.3.3/functional-task-manager-fastopt/main.js public/

# 3. Open the public/index.html file in your browser
open public/index.html   # (macOS)
xdg-open public/index.html # (Linux)
```
*(Alternatively, you can just manually drag and drop `public/index.html` into your web browser after step 2).*

## Deploy to Vercel

This repository is designed for fully automated deployment to Vercel directly from GitHub.

**Exact steps:**

1. **Push to GitHub:** Commit all files and push this project repository to GitHub.
2. **Import repo in Vercel:** Go to your Vercel dashboard, click "Add New... Project", and import your GitHub repository.
3. **Build command:** In the project settings during setup, ensure the **Build Command** is set to:
   ```bash
   bash build.sh
   ```
4. **Output directory:** Ensure the **Output Directory** is set to:
   ```text
   public
   ```
   *(Note: The provided `vercel.json` already defines this configuration natively).*

Vercel will run `build.sh` (which downloads Scala/sbt, compiles the production JS bundle via `sbt fullLinkJS`, and moves the JS file to `public/`) and then serve the `public/` folder natively. No manual post-processing is required.

## Screenshots Placeholder

![Main Application View](placeholder-main-view.png)
![Filtered View](placeholder-filtered-view.png)
