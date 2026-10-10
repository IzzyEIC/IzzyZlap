package pl.izzy.zlap.capture

import android.app.PendingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

data class RecentMessage(
    val key: String,
    val packageName: String,
    val appName: String,
    val sender: String?,
    val text: String?,
    val postedAt: Long,
    /** Otwiera dokładnie tę rozmowę – działa, dopóki proces Złap żyje (czyli do restartu). */
    val contentIntent: PendingIntent?,
    /** Trwały link, jeśli udało się go ustalić z numeru telefonu. */
    val link: String?,
)

/** Ostatnie powiadomienia z innych aplikacji, trzymane w pamięci. */
object RecentStore {
    private const val MAX_ITEMS = 30
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    private val _items = MutableStateFlow<List<RecentMessage>>(emptyList())
    val items: StateFlow<List<RecentMessage>> = _items

    private val taskIntents = ConcurrentHashMap<Long, PendingIntent>()

    /** Ta sama rozmowa (ten sam klucz powiadomienia) zastępuje poprzedni wpis. */
    fun add(message: RecentMessage) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        _items.update { current ->
            (listOf(message) + current.filterNot { it.key == message.key })
                .filter { it.postedAt >= cutoff }
                .sortedByDescending { it.postedAt }
                .take(MAX_ITEMS)
        }
    }

    fun attach(taskId: Long, intent: PendingIntent) {
        taskIntents[taskId] = intent
    }

    fun intentFor(taskId: Long): PendingIntent? = taskIntents[taskId]
}
