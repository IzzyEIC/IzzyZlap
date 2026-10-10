package pl.izzy.zlap.capture

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process

/** Z której aplikacji stuknięto bańkę – na podstawie „Dostępu do danych o użyciu”. */
object ForegroundApp {
    private const val LOOKBACK_MS = 10 * 60 * 1000L
    private val IGNORED = setOf("com.android.systemui", "android")

    sealed interface Where {
        /** Pulpit (launcher) – łapiemy z paska powiadomień. */
        data object Home : Where
        data class App(val packageName: String, val label: String) : Where
        /** Brak uprawnienia – nie wiemy. */
        data object Unknown : Where
    }

    fun hasPermission(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun detect(c: Context): Where {
        if (!hasPermission(c)) return Where.Unknown
        val pkg = lastResumedPackage(c) ?: return Where.Unknown
        return if (pkg == launcherPackage(c)) Where.Home else Where.App(pkg, label(c, pkg))
    }

    private fun lastResumedPackage(c: Context): String? {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                event.packageName != c.packageName &&
                event.packageName !in IGNORED
            ) {
                last = event.packageName
            }
        }
        return last
    }

    private fun launcherPackage(c: Context): String? =
        c.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName

    private fun label(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationInfo(pkg, 0).loadLabel(c.packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
