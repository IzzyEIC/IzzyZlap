package pl.izzy.zlap

import android.app.Application
import android.content.Context
import pl.izzy.zlap.data.TaskRepository
import pl.izzy.zlap.data.ZlapDatabase
import pl.izzy.zlap.reminders.Notifier

class ZlapApp : Application() {
    val database by lazy { ZlapDatabase.create(this) }
    val settings by lazy { ZlapSettings(this) }
    val tasks by lazy { TaskRepository(this, database.taskDao()) }

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
    }
}

val Context.app: ZlapApp get() = applicationContext as ZlapApp
