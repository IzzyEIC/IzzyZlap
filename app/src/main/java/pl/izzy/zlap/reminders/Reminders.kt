package pl.izzy.zlap.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.izzy.zlap.ZlapSettings
import pl.izzy.zlap.app
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Jeden alarm na raz: zawsze ustawiony na najbliższe przypomnienie.
 * Sloty: start, start+interwał, ... aż do końca okna; potem następny dzień.
 * Brak otwartych zadań = brak alarmu.
 */
object Reminders {
    private const val REQ_REMINDER = 1
    private const val REQ_TEST = 2
    const val EXTRA_TEST = "test"

    suspend fun reschedule(context: Context) {
        val pi = pendingIntent(context, REQ_REMINDER, test = false)
        val earliest = context.app.tasks.earliestOpenDay()
        if (earliest == null) {
            context.getSystemService(AlarmManager::class.java).cancel(pi)
            return
        }
        val next = nextFire(LocalDateTime.now(), earliest, context.app.settings)
        setExact(context, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    /** Po restarcie telefonu: jeśli jesteśmy w oknie przypomnień, nie czekamy – pokazujemy od razu. */
    suspend fun catchUp(context: Context) {
        if (context.app.settings.isInWindow(LocalTime.now())) {
            val due = context.app.tasks.dueToday()
            if (due.isNotEmpty()) Notifier.showReminder(context, due, alert = true)
        }
        reschedule(context)
    }

    fun scheduleTest(context: Context, inSeconds: Long = 60) {
        setExact(context, System.currentTimeMillis() + inSeconds * 1000, pendingIntent(context, REQ_TEST, test = true))
    }

    fun nextFire(now: LocalDateTime, earliestDue: LocalDate, s: ZlapSettings): LocalDateTime {
        val today = now.toLocalDate()
        if (earliestDue.isAfter(today)) return earliestDue.atTime(s.startTime)

        val startToday = today.atTime(s.startTime)
        val endToday = today.atTime(s.endTime)
        if (now.isBefore(startToday)) return startToday

        val step = s.intervalMinutes.coerceAtLeast(1).toLong()
        val sinceStart = Duration.between(startToday, now).toMinutes()
        val next = startToday.plusMinutes((sinceStart / step + 1) * step)
        return if (!next.isAfter(endToday)) next else today.plusDays(1).atTime(s.startTime)
    }

    private fun pendingIntent(context: Context, requestCode: Int, test: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TEST, test),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setExact(context: Context, atMillis: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }
}

/** Receiver może pracować w tle kilka sekund, co wystarcza na odczyt bazy. */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
