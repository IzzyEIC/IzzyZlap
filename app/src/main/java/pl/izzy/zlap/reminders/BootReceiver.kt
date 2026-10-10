package pl.izzy.zlap.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.izzy.zlap.capture.BubbleService

/** Alarmy znikają przy restarcie telefonu – tu je odtwarzamy. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        BubbleService.sync(context)
        Reminders.catchUp(context)
    }
}
