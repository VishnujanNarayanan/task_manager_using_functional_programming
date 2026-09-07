package taskmanager

import org.scalajs.dom
import scala.util.Try

/** The one place task state leaves the application.
  *
  * Everything either side of this object is pure: `TaskCodec` does the
  * translation, the transformations in `Main` do the work. This is the only
  * code that touches the browser, and it is deliberately tiny.
  *
  * Both operations swallow failure on purpose. `localStorage` throws rather
  * than returning an error when storage is disabled, when a browser is in a
  * private mode that forbids it, or when the origin's quota is exhausted --
  * and an unguarded write would take down `addTask` for those visitors. A
  * planner that forgets is worth more than a planner that crashes.
  */
object TaskStorage {

  /** `None` when nothing is stored, when storage is unreachable, or when what
    * is there cannot be decoded. Callers fall back to the seed list.
    */
  def load(): Option[List[Task]] =
    for {
      raw   <- Try(Option(dom.window.localStorage.getItem(TaskCodec.StorageKey))).toOption.flatten
      tasks <- TaskCodec.decode(raw).toOption
    } yield tasks

  def save(tasks: List[Task]): Unit =
    Try(dom.window.localStorage.setItem(TaskCodec.StorageKey, TaskCodec.encode(tasks)))
      .fold(_ => (), _ => ())
}
