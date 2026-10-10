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
import androidx.core.app.NotificationManagerCompat
import pl.izzy.zlap.ZlapSettings
import pl.izzy.zlap.capture.CaptureListener
import pl.izzy.zlap.capture.ChatReaderService
import pl.izzy.zlap.capture.ForegroundApp

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

    fun listenerOk(c: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun overlayOk(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun usageOk(c: Context): Boolean = ForegroundApp.hasPermission(c)

    fun chatReaderOk(c: Context): Boolean = ChatReaderService.isEnabled(c)

    fun openAccessibilitySettings(c: Context) {
        start(c, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openUsageSettings(c: Context) {
        try {
            c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(c)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            start(c, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    fun needsAttention(c: Context, s: ZlapSettings): Boolean =
        !notificationsOk(c) || !exactAlarmsOk(c) || !batteryOk(c) || !listenerOk(c) || !usageOk(c) || !ChatReaderService.isEnabled(c) ||
            (s.bubbleEnabled && !overlayOk(c)) ||
            (isXiaomi && (!s.autostartConfirmed || (s.bubbleEnabled && !s.popupConfirmed)))

    fun openListenerSettings(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            start(
                c,
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(c, CaptureListener::class.java).flattenToString(),
                ),
            )
        } else {
            start(c, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    fun openOverlaySettings(c: Context) {
        start(c, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(c)))
    }

    /** MIUI: ekran „Inne uprawnienia” z „Wyświetlaj wyskakujące okna w tle”. */
    fun openMiuiPermissions(c: Context) {
        start(
            c,
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", c.packageName),
        )
    }

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
