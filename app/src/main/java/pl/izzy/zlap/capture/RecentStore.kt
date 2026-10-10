package pl.izzy.zlap.capture

import android.app.PendingIntent
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.izzy.zlap.app
import pl.izzy.zlap.data.RecentEntity
import java.util.concurrent.ConcurrentHashMap

data class RecentMessage(
    val key: String,
    val packageName: String,
    val appName: String,
    val sender: String?,
    val text: String?,
    val postedAt: Long,
    /** Otwiera dokładnie tę rozmowę – dostępne, dopóki proces Złap żyje. */
    val contentIntent: PendingIntent?,
    /** Trwały link, jeśli udało się go ustalić z numeru telefonu. */
    val link: String?,
)

/**
 * Ostatnie powiadomienia z innych aplikacji. Treść jest w bazie (przetrwa zamknięcie
 * aplikacji przez system), a PendingIntenty – których nie da się zapisać – tylko w pamięci.
 */
object RecentStore {
    private const val MAX_ITEMS = 30
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val intents = ConcurrentHashMap<String, PendingIntent>()
    private val taskIntents = ConcurrentHashMap<Long, PendingIntent>()

    /** Ta sama rozmowa (ten sam klucz powiadomienia) zastępuje poprzedni wpis. */
    fun add(context: Context, message: RecentMessage) {
        message.contentIntent?.let { intents[message.key] = it }
        val dao = context.app.database.recentDao()
        scope.launch {
            dao.upsert(
                RecentEntity(
                    key = message.key,
                    packageName = message.packageName,
                    appName = message.appName,
                    sender = message.sender,
                    text = message.text,
                    postedAt = message.postedAt,
                    link = message.link,
                )
            )
            dao.deleteOlderThan(System.currentTimeMillis() - MAX_AGE_MS)
        }
    }

    fun items(context: Context): Flow<List<RecentMessage>> =
        context.app.database.recentDao()
            .observe(System.currentTimeMillis() - MAX_AGE_MS, MAX_ITEMS)
            .map { list ->
                list.map {
                    RecentMessage(
                        key = it.key,
                        packageName = it.packageName,
                        appName = it.appName,
                        sender = it.sender,
                        text = it.text,
                        postedAt = it.postedAt,
                        contentIntent = intents[it.key],
                        link = it.link,
                    )
                }
            }

    fun attach(taskId: Long, intent: PendingIntent) {
        taskIntents[taskId] = intent
    }

    fun intentFor(taskId: Long): PendingIntent? = taskIntents[taskId]
}
