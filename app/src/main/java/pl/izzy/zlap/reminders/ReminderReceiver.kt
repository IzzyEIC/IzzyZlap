package pl.izzy.zlap.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.izzy.zlap.app
import java.time.LocalTime

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        if (intent.getBooleanExtra(Reminders.EXTRA_TEST, false)) {
            Notifier.showTest(context)
        } else {
            // MIUI potrafi dostarczyć alarm z opóźnieniem – po godzinie ciszy nie przypominamy
            if (context.app.settings.isInWindow(LocalTime.now(), graceMinutes = 5)) {
                Notifier.showReminder(context, context.app.tasks.dueToday(), alert = true)
            }
            Reminders.reschedule(context)
        }
    }
}
