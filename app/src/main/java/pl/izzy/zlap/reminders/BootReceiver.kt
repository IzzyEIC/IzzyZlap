package pl.izzy.zlap.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarmy znikają przy restarcie telefonu – tu je odtwarzamy. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        Reminders.catchUp(context)
    }
}
