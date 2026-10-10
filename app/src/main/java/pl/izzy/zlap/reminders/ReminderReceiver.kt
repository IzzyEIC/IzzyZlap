package pl.izzy.zlap.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.izzy.zlap.app

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        if (intent.getBooleanExtra(Reminders.EXTRA_TEST, false)) {
            Notifier.showTest(context)
        } else {
            Notifier.showReminder(context, context.app.tasks.dueToday(), alert = true)
            Reminders.reschedule(context)
        }
    }
}
