package pl.izzy.zlap.setup

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import pl.izzy.zlap.ZlapSettings

/** Sprawdzanie i otwieranie ustawień, bez których Xiaomi usypia przypomnienia. */
object PhoneSetup {
    val isXiaomi: Boolean =
        listOf("xiaomi", "redmi", "poco").any {
            Build.MANUFACTURER.equals(it, ignoreCase = true) || Build.BRAND.equals(it, ignoreCase = true)
        }

    fun notificationsOk(c: Context): Boolean =
        c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun exactAlarmsOk(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun batteryOk(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    fun needsAttention(c: Context, s: ZlapSettings): Boolean =
        !notificationsOk(c) || !exactAlarmsOk(c) || !batteryOk(c) || (isXiaomi && !s.autostartConfirmed)

    fun openNotificationSettings(c: Context) {
        start(c, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName))
    }

    fun openExactAlarmSettings(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            start(c, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(c)))
        } else {
            openAppDetails(c)
        }
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryExemption(c: Context) {
        start(c, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(c)))
    }

    fun openAutostart(c: Context) {
        start(
            c,
            Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            ),
        )
    }

    fun openAppDetails(c: Context) {
        start(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(c)), fallback = false)
    }

    private fun packageUri(c: Context): Uri = Uri.parse("package:${c.packageName}")

    /** Jeśli konkretny ekran nie istnieje na tym telefonie, otwieramy ogólne informacje o aplikacji. */
    private fun start(c: Context, intent: Intent, fallback: Boolean = true) {
        try {
            c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            if (fallback) openAppDetails(c)
        } catch (e: SecurityException) {
            if (fallback) openAppDetails(c)
        }
    }
}
