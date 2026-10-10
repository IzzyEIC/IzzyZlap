package pl.izzy.zlap.capture

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/** Zapamiętuje przychodzące powiadomienia, żeby przy łapaniu było wiadomo, od kogo i skąd. */
class CaptureListener : NotificationListenerService() {

    override fun onListenerConnected() {
        try {
            activeNotifications?.sortedBy { it.postTime }?.forEach(::record)
        } catch (e: SecurityException) {
            // Uprawnienie właśnie cofnięte
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = record(sbn)

    private fun record(sbn: StatusBarNotification) {
        parse(this, sbn)?.let(RecentStore::add)
    }

    companion object {
        private val SKIPPED_CATEGORIES = setOf(
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_STATUS,
        )

        private val SMS_APPS = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
        )

        fun parse(context: Context, sbn: StatusBarNotification): RecentMessage? {
            if (sbn.packageName == context.packageName || sbn.isOngoing) return null
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
            if (n.category in SKIPPED_CATEGORIES) return null

            val extras = n.extras
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
            val last = style?.messages?.lastOrNull()

            // W grupie: „Nazwa grupy · Kasia”, w rozmowie prywatnej: „Kasia”
            val sender = listOfNotNull(
                style?.conversationTitle?.toString(),
                last?.person?.name?.toString(),
            ).distinct().joinToString(" · ").ifBlank { null }
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

            val text = last?.text?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            if (sender.isNullOrBlank() && text.isNullOrBlank()) return null

            return RecentMessage(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appLabel(context, sbn.packageName),
                sender = sender?.trim(),
                text = text?.trim(),
                postedAt = sbn.postTime,
                contentIntent = n.contentIntent,
                link = linkFor(context, sbn.packageName, last?.person?.uri, last?.person?.key),
            )
        }

        private fun linkFor(context: Context, pkg: String, uri: String?, key: String?): String? {
            val number = when {
                uri?.startsWith("tel:") == true -> uri.removePrefix("tel:")
                key?.endsWith("@s.whatsapp.net") == true -> key.substringBefore("@")
                else -> null
            }?.filter { it.isDigit() || it == '+' }?.ifEmpty { null } ?: return null

            return when {
                pkg.startsWith("com.whatsapp") -> "https://wa.me/${number.trimStart('+')}"
                pkg in SMS_APPS || pkg == Telephony.Sms.getDefaultSmsPackage(context) -> "smsto:$number"
                else -> null
            }
        }

        private fun appLabel(context: Context, pkg: String): String = try {
            val pm = context.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }
}
