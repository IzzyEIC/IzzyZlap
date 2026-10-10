package pl.izzy.zlap.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import pl.izzy.zlap.reminders.Notifier
import pl.izzy.zlap.reminders.Reminders
import java.time.LocalDate

/** Każda zmiana listy od razu przestawia alarm i odświeża widoczne powiadomienie. */
class TaskRepository(private val context: Context, private val dao: TaskDao) {

    val open: Flow<List<Task>> = dao.observeOpen()

    suspend fun add(
        title: String,
        due: LocalDate,
        sourcePackage: String? = null,
        sourceApp: String? = null,
    ) {
        dao.insert(
            Task(
                title = title.trim(),
                dueEpochDay = due.toEpochDay(),
                sourcePackage = sourcePackage,
                sourceApp = sourceApp,
            )
        )
        changed()
    }

    suspend fun complete(task: Task) {
        dao.update(task.copy(done = true, doneAt = System.currentTimeMillis()))
        changed()
    }

    suspend fun moveTo(task: Task, day: LocalDate) {
        dao.update(task.copy(dueEpochDay = day.toEpochDay()))
        changed()
    }

    suspend fun delete(task: Task) {
        dao.delete(task)
        changed()
    }

    /** Cofnięcie usunięcia/zrobienia: przywraca zadanie dokładnie w poprzednim stanie. */
    suspend fun restore(task: Task) {
        dao.upsert(task)
        changed()
    }

    suspend fun dueToday(): List<Task> = dao.dueOnOrBefore(LocalDate.now().toEpochDay())

    suspend fun earliestOpenDay(): LocalDate? = dao.earliestOpenDay()?.let(LocalDate::ofEpochDay)

    private suspend fun changed() {
        Reminders.reschedule(context)
        Notifier.refreshIfShown(context, dueToday())
    }
}
