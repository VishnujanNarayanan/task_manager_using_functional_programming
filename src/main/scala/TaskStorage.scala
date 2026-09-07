package taskmanager

import org.scalajs.dom
import scala.util.Try

/** The one place task state leaves the application.
  *
  * Everything either side of this object is pure: `TaskCodec` does the
  * translation and the decisions, the transformations in `Main` do the work.
  * This is the only code that touches the browser, and it is deliberately tiny
  * -- every decision it makes has been pushed into `TaskCodec` so it can be
  * tested without one.
  *
  * Reads and writes swallow failure on purpose. `localStorage` throws rather
  * than returning an error when storage is disabled, when a private mode
  * forbids it, or when the origin's quota is exhausted -- and an unguarded
  * write would take down `addTask` for those visitors. A planner that forgets
  * is worth more than a planner that crashes.
  */
object TaskStorage {

  /** Where a payload we could not decode is kept.
    *
    * Without this the data is destroyed by the very next edit: we fall back to
    * the seed list, the write edge fires, and the unreadable-but-intact payload
    * is overwritten. Moving it aside costs one key and makes the failure
    * recoverable by hand instead of terminal.
    */
  val QuarantineKey: String = "functional-task-manager.v1.unreadable"

  /** Reads stored tasks, quarantining anything that will not decode.
    *
    * The quarantine is written only when nothing is quarantined already, so the
    * first failure -- the one still holding the original data -- is the one
    * that survives.
    */
  def load(): LoadResult = {
    val raw    = read(TaskCodec.StorageKey)
    val result = TaskCodec.interpretLoad(raw)

    result match {
      case LoadResult.Unreadable(_) if read(QuarantineKey).isEmpty =>
        raw.foreach(write(QuarantineKey, _))
      case _ => ()
    }

    result
  }

  def save(tasks: List[Task]): Unit =
    write(TaskCodec.StorageKey, TaskCodec.encode(tasks))

  /** Registers `handle` for task-list writes made by OTHER tabs.
    *
    * The `storage` event never fires in the tab that performed the write, so
    * this only ever reports somebody else's change. Without it, two open tabs
    * each hold their own list and stamp the whole thing over the key -- so a
    * tab that never saw a task added elsewhere silently deletes it on its next
    * write.
    */
  def onExternalChange(current: () => List[Task], handle: List[Task] => Unit): Unit =
    guard {
      dom.window.addEventListener[dom.StorageEvent](
        "storage",
        (event: dom.StorageEvent) =>
          TaskCodec
            .interpretExternalChange(event.key, Option(event.newValue), current())
            .foreach(handle)
      )
    }

  // --- browser edge ----------------------------------------------------

  private def read(key: String): Option[String] =
    Try(Option(dom.window.localStorage.getItem(key))).toOption.flatten.filter(_.nonEmpty)

  private def write(key: String, value: String): Unit =
    guard(dom.window.localStorage.setItem(key, value))

  private def guard(effect: => Unit): Unit =
    Try(effect).fold(_ => (), _ => ())
}
