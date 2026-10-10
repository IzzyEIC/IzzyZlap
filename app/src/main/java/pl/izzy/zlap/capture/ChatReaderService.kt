package pl.izzy.zlap.capture

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Aplikacja pod bańką i – w komunikatorach – nazwa otwartej rozmowy. */
data class ChatInfo(val packageName: String, val title: String?)

/**
 * Ułatwienia dostępu użyte wyłącznie na żądanie: w chwili stuknięcia bańki odczytujemy
 * nazwę otwartej rozmowy z górnego paska. Zdarzeń w tle nie przetwarzamy.
 */
class ChatReaderService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: ChatReaderService? = null

        /** Dokładne identyfikatory paska z nazwą rozmowy. */
        private val TITLE_IDS = listOf(
            "com.whatsapp:id/conversation_contact_name",
            "com.whatsapp.w4b:id/conversation_contact_name",
        )

        /** Komunikatory, w których tytuł u góry ekranu to nazwa rozmowy. */
        private val MESSENGERS = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.instagram.android",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.viber.voip",
        )

        fun isEnabled(c: Context): Boolean {
            val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(c, ChatReaderService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Wywoływane przy stuknięciu bańki – zanim okienko Złap przykryje ekran. */
        fun readCurrentChat(): ChatInfo? {
            val service = instance ?: return null
            val root = service.rootInActiveWindow ?: return null
            val pkg = root.packageName?.toString() ?: return null
            if (pkg == service.packageName) return null
            if (pkg !in MESSENGERS) return ChatInfo(pkg, null)

            val title = TITLE_IDS.firstNotNullOfOrNull { id ->
                root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.text?.toString()
            } ?: topTitle(service, root)

            // W trybie zaznaczania wiadomości pasek pokazuje samą liczbę („1”) – to nie jest nazwa
            return ChatInfo(pkg, title?.trim()?.takeIf { it.isNotEmpty() && !it.all(Char::isDigit) })
        }

        /** Pierwszy tekst w górnym pasku ekranu – tytuł rozmowy w większości komunikatorów. */
        private fun topTitle(service: AccessibilityService, root: AccessibilityNodeInfo): String? {
            val maxBottom = service.resources.displayMetrics.heightPixels * 0.18
            val bounds = Rect()
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var visited = 0
            while (queue.isNotEmpty() && visited < 400) {
                val node = queue.removeFirst()
                visited++
                node.getBoundsInScreen(bounds)
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrEmpty() && !node.isEditable && bounds.top > 0 && bounds.bottom < maxBottom &&
                    text.length in 2..60
                ) {
                    return text
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            }
            return null
        }
    }
}
