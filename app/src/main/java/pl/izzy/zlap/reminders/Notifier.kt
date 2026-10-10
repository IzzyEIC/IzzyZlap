package pl.izzy.zlap.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import pl.izzy.zlap.R
import pl.izzy.zlap.app
import pl.izzy.zlap.data.Task
import pl.izzy.zlap.data.tidy
import pl.izzy.zlap.ui.MainActivity

/** Zawsze jedno zbiorcze powiadomienie zamiast osobnego dla każdej rzeczy. */
object Notifier {
    private const val CHANNEL_PREFIX = "reminders"
    private const val REMINDER_ID = 1001
    private const val TEST_ID = 1002
    private const val MAX_LINES = 6

    private fun channelId(context: Context): String {
        val version = context.app.settings.channelVersion
        return if (version == 0) CHANNEL_PREFIX else "${CHANNEL_PREFIX}_$version"
    }

    fun ensureChannel(context: Context) {
        val sound = context.app.settings.soundUri
        val channel = NotificationChannel(
            channelId(context),
            "Przypomnienia",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Wieczorna lista rzeczy do zrobienia"
            if (sound != null) {
                setSound(
                    Uri.parse(sound),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        }
        manager(context).createNotificationChannel(channel)
    }

    /** Nowy dźwięk = nowy kanał (Android nie pozwala zmienić dźwięku istniejącego kanału). */
    fun applySound(context: Context) {
        val settings = context.app.settings
        settings.channelVersion = settings.channelVersion + 1
        val nm = manager(context)
        val current = channelId(context)
        nm.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != current }
            .forEach { nm.deleteNotificationChannel(it.id) }
        ensureChannel(context)
    }

    /** Pokazuje przypomnienie od razu, żeby usłyszeć dźwięk. */
    fun playSample(context: Context) {
        val nm = manager(context)
        if (!nm.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_stat_zlap)
            .setContentTitle("Tak brzmi przypomnienie")
            .setContentText(context.app.settings.soundName ?: "Domyślny dźwięk")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(30_000)
            .setAutoCancel(true)
            .build()
        nm.notify(TEST_ID, notification)
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
        due.take(MAX_LINES).forEach { style.addLine("• ${it.label}") }
        if (due.size > MAX_LINES) style.setSummaryText("+ jeszcze ${due.size - MAX_LINES}")

        val notification = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_stat_zlap)
            .setContentTitle(title)
            .setContentText(due.joinToString(", ") { it.label })
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
        val notification = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_stat_zlap)
            .setContentTitle("Złap działa ✓")
            .setContentText("Przypomnienia dotrą też przy zablokowanym ekranie.")
            .setContentIntent(openApp(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(TEST_ID, notification)
    }

    private const val BUBBLE_CHANNEL_ID = "bubble"
    const val BUBBLE_NOTIFICATION_ID = 1003

    /** Stałe, ciche powiadomienie, bez którego Android nie pozwala bańce działać w tle. */
    fun bubbleNotification(context: Context): android.app.Notification {
        manager(context).createNotificationChannel(
            NotificationChannel(BUBBLE_CHANNEL_ID, "Bańka Złap", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Stałe powiadomienie, dzięki któremu bańka działa nad innymi aplikacjami"
                setShowBadge(false)
            }
        )
        val capture = PendingIntent.getActivity(
            context,
            1,
            pl.izzy.zlap.capture.CaptureActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, BUBBLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zlap)
            .setContentTitle("Złap jest gotowa")
            .setContentText("Stuknij, żeby złapać coś na później")
            .setContentIntent(capture)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private val Task.label: String
        get() = title.tidy().replace('\n', ' ').let { if (sender != null) "$sender: $it" else it }

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
