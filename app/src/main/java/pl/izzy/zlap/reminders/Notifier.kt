package pl.izzy.zlap.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import pl.izzy.zlap.R
import pl.izzy.zlap.data.Task
import pl.izzy.zlap.ui.MainActivity

/** Zawsze jedno zbiorcze powiadomienie zamiast osobnego dla każdej rzeczy. */
object Notifier {
    private const val CHANNEL_ID = "reminders"
    private const val REMINDER_ID = 1001
    private const val TEST_ID = 1002
    private const val MAX_LINES = 6

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Przypomnienia",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Wieczorna lista rzeczy do zrobienia" }
        manager(context).createNotificationChannel(channel)
    }

    /** [alert] = false: ciche odświeżenie treści już widocznego powiadomienia. */
    fun showReminder(context: Context, due: List<Task>, alert: Boolean) {
        val nm = manager(context)
        if (due.isEmpty()) {
            nm.cancel(REMINDER_ID)
            return
        }
        if (!nm.areNotificationsEnabled()) return

        val title = if (due.size == 1) "Masz 1 rzecz do zrobienia" else "Masz ${due.size} rzeczy do zrobienia"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        due.take(MAX_LINES).forEach { style.addLine("• ${it.title}") }
        if (due.size > MAX_LINES) style.setSummaryText("+ jeszcze ${due.size - MAX_LINES}")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zlap)
            .setContentTitle(title)
            .setContentText(due.joinToString(", ") { it.title })
            .setStyle(style)
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(!alert)
            .setNumber(due.size)
            .build()
        nm.notify(REMINDER_ID, notification)
    }

    fun refreshIfShown(context: Context, due: List<Task>) {
        if (manager(context).activeNotifications.any { it.id == REMINDER_ID }) {
            showReminder(context, due, alert = false)
        }
    }

    fun showTest(context: Context) {
        val nm = manager(context)
        if (!nm.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zlap)
            .setContentTitle("Złap działa ✓")
            .setContentText("Przypomnienia dotrą też przy zablokowanym ekranie.")
            .setContentIntent(openApp(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(TEST_ID, notification)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
