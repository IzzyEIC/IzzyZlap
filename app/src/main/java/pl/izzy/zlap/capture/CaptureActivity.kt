package pl.izzy.zlap.capture

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.izzy.zlap.app
import pl.izzy.zlap.data.tidy
import pl.izzy.zlap.setup.PhoneSetup
import pl.izzy.zlap.ui.ZlapTheme
import pl.izzy.zlap.ui.dayLabel
import pl.izzy.zlap.ui.formatMinutes
import pl.izzy.zlap.ui.pickDate
import java.time.LocalDate

/**
 * Okienko łapania nad bieżącą aplikacją (np. WhatsAppem).
 * Źródła tekstu, od najważniejszego: zaznaczenie/udostępnienie → świeżo skopiowany tekst → treść powiadomienia.
 */
class CaptureActivity : ComponentActivity() {
    private val clipText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CaptureListener.ensureConnected(this)
        val sharedLink = sharedLink(intent)
        val shared = sharedLink?.text ?: sharedText(intent)
        val chat = intent.getStringExtra(EXTRA_CHAT_PACKAGE)?.let { ChatInfo(it, intent.getStringExtra(EXTRA_CHAT_TITLE)) }
        setContent {
            ZlapTheme {
                CaptureSheet(
                    sharedText = shared,
                    sharedLink = sharedLink,
                    chat = chat,
                    clipText = clipText.value,
                    onClose = { finish() },
                )
            }
        }
    }

    // Schowek można odczytać dopiero, gdy okno ma fokus (ograniczenie Androida 10+)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && clipText.value == null) clipText.value = freshClipboardText()
    }

    private fun freshClipboardText(): String? {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip ?: return null
        val age = System.currentTimeMillis() - clip.description.timestamp
        if (age > CLIP_MAX_AGE_MS) return null
        return clip.getItemAt(0)?.coerceToText(this)?.toString()?.trim()?.ifEmpty { null }
    }

    /**
     * „Udostępnij” z linkiem (YouTube, przeglądarka, Instagram...): zapamiętujemy link,
     * tytuł (temat udostępnienia) i aplikację, z której przyszedł.
     */
    private fun sharedLink(intent: Intent): RecentMessage? {
        if (intent.action != Intent.ACTION_SEND) return null
        val body = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val url = URL_REGEX.find(body)?.value ?: return null
        val pkg = referrer
            ?.takeIf { it.scheme == "android-app" }
            ?.host
            ?.takeIf { it != packageName && it != "android" }
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.tidy()?.ifEmpty { null }
            ?: body.replace(url, "").tidy().ifEmpty { null }
            ?: url
        return RecentMessage(
            key = "share:$url",
            packageName = pkg.orEmpty(),
            appName = pkg?.let { ForegroundApp.label(this, it) } ?: Uri.parse(url).host.orEmpty(),
            sender = null,
            text = title,
            postedAt = System.currentTimeMillis(),
            contentIntent = null,
            link = url,
        )
    }

    private fun sharedText(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND -> listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.getStringExtra(Intent.EXTRA_TEXT),
        ).joinToString("\n").ifEmpty { null }
        else -> null
    }?.trim()

    companion object {
        private const val CLIP_MAX_AGE_MS = 5 * 60 * 1000L
        private val URL_REGEX = Regex("https?://\\S+")

        private const val EXTRA_CHAT_PACKAGE = "chatPackage"
        private const val EXTRA_CHAT_TITLE = "chatTitle"

        fun intent(context: Context, chat: ChatInfo? = null): Intent =
            Intent(context, CaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .apply {
                    if (chat != null) {
                        putExtra(EXTRA_CHAT_PACKAGE, chat.packageName)
                        putExtra(EXTRA_CHAT_TITLE, chat.title)
                    }
                }
    }
}

private const val PRESELECT_MAX_AGE_MS = 30 * 60 * 1000L

private enum class Mode { App, Shade, All }

/** Rozmowa otwarta na ekranie trafia na górę listy – z ostatnią wiadomością, jeśli była. */
private fun withOpenChatFirst(list: List<RecentMessage>, openChat: RecentMessage?): List<RecentMessage> {
    val title = openChat?.sender ?: return list
    val match = list.firstOrNull { message ->
        val sender = message.sender ?: return@firstOrNull false
        sender.equals(title, ignoreCase = true) ||
            sender.contains(title, ignoreCase = true) ||
            title.contains(sender, ignoreCase = true)
    }
    return if (match != null) listOf(match) + (list - match) else listOf(openChat) + list
}

/** Treść wiadomości, a gdy jej nie znamy (czat bez powiadomienia) – „Odpisać”. */
private fun suggestedText(message: RecentMessage?): String = when {
    message == null -> ""
    message.text != null -> message.text
    else -> "Odpisać"
}

@Composable
private fun CaptureSheet(sharedText: String?, sharedLink: RecentMessage?, chat: ChatInfo?, clipText: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // null = jeszcze się wczytuje
    val loaded by remember { RecentStore.items(context) }
        .collectAsStateWithLifecycle<List<RecentMessage>?>(initialValue = null)
    val recent = loaded.orEmpty()
    val listenerOk = remember { PhoneSetup.listenerOk(context) }
    val readerOn = remember { PhoneSetup.chatReaderOk(context) }
    // Ułatwienia dostępu wiedzą najdokładniej; bez nich – dane o użyciu
    val where = remember {
        chat?.let { ForegroundApp.Where.App(it.packageName, ForegroundApp.label(context, it.packageName)) }
            ?: ForegroundApp.detect(context)
    }
    val appPackage = (where as? ForegroundApp.Where.App)?.packageName
    // Otwarty czat, z którego nie było powiadomienia – i tak chcemy go móc złapać
    val openChat = remember {
        val title = chat?.title
        val app = where as? ForegroundApp.Where.App
        if (title != null && app != null) {
            RecentMessage("chat:${app.packageName}:$title", app.packageName, app.label, title, null, System.currentTimeMillis(), null, null)
        } else {
            null
        }
    }
    val shade = remember { CaptureListener.visibleNow(context) }
    var mode by remember {
        mutableStateOf(
            when {
                where is ForegroundApp.Where.App -> Mode.App
                shade != null -> Mode.Shade
                else -> Mode.All
            }
        )
    }
    // Udostępniony link zawsze na górze, w każdym trybie
    val shown = listOfNotNull(sharedLink) + when (mode) {
        Mode.App -> withOpenChatFirst(recent.filter { it.packageName == appPackage }, openChat)
        Mode.Shade -> shade.orEmpty()
        Mode.All -> recent
    }
    val today = remember { LocalDate.now() }
    val tomorrow = remember { today.plusDays(1) }

    var selected by remember { mutableStateOf<RecentMessage?>(null) }
    var text by remember { mutableStateOf(sharedText.orEmpty()) }
    var edited by remember { mutableStateOf(sharedText != null) }
    var preselectedFor by remember { mutableStateOf<Mode?>(null) }

    // Po wczytaniu listy (i po zmianie źródła) zaznaczamy najświeższą wiadomość.
    // W trybie aplikacji zawsze – to niemal na pewno ta, na którą właśnie patrzysz.
    LaunchedEffect(shown, mode) {
        if (preselectedFor != mode && shown.isNotEmpty() && (mode == Mode.Shade || loaded != null)) {
            preselectedFor = mode
            selected = if (mode == Mode.App) {
                shown.first()
            } else {
                shown.firstOrNull { System.currentTimeMillis() - it.postedAt < PRESELECT_MAX_AGE_MS }
            }
            if (!edited) text = suggestedText(selected)
        }
    }
    var due by remember { mutableStateOf(context.app.settings.defaultDueDate()) }

    // Skopiowany tekst ma pierwszeństwo przed treścią powiadomienia
    LaunchedEffect(clipText) {
        if (clipText != null && sharedText == null && !edited) {
            text = clipText
            edited = true
        }
    }

    fun save() {
        if (text.isBlank()) return
        val source = selected
        scope.launch {
            val id = context.app.tasks.add(
                title = text,
                due = due,
                sourcePackage = source?.packageName?.ifEmpty { null },
                sourceApp = source?.appName,
                sender = source?.sender,
                link = source?.link,
            )
            source?.contentIntent?.let { RecentStore.attach(id, it) }
            Toast.makeText(context, "Złapane ✓ Przypomnę ${context.app.settings.reminderLabel(due)}", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .imePadding(),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Złap na później", style = MaterialTheme.typography.titleLarge)
                if (readerOn && where != ForegroundApp.Where.Home) {
                    Text(
                        when {
                            chat?.title != null -> "Otwarta rozmowa: ${chat.title}"
                            chat != null -> "Aplikacja: ${ForegroundApp.label(context, chat.packageName)} (nie rozpoznano rozmowy)"
                            else -> "Nie udało się odczytać ekranu"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!listenerOk) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Włącz dostęp do powiadomień, żeby Złap widziała, od kogo jest wiadomość.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { PhoneSetup.openListenerSettings(context) }) { Text("Włącz") }
                        }
                    }
                }

                if (listenerOk) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (where is ForegroundApp.Where.App) {
                            FilterChip(
                                selected = mode == Mode.App,
                                onClick = { mode = Mode.App },
                                label = { Text(where.label) },
                            )
                        }
                        if (shade != null) {
                            FilterChip(
                                selected = mode == Mode.Shade,
                                onClick = { mode = Mode.Shade },
                                label = { Text("Pasek powiadomień") },
                            )
                        }
                        FilterChip(
                            selected = mode == Mode.All,
                            onClick = { mode = Mode.All },
                            label = { Text("Ostatnie 24 h") },
                        )
                    }
                    if (where == ForegroundApp.Where.Unknown) {
                        Text(
                            "Włącz „Dostęp do danych o użyciu” w konfiguracji, a Złap sama pokaże wiadomości z aplikacji, w której jesteś.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (listenerOk && !CaptureListener.isConnected) {
                    Text(
                        "Złap jest chwilowo odłączona od powiadomień – podłączam ponownie. " +
                            "Jeśli to się powtarza: wyłącz i włącz jej dostęp do powiadomień (krok 6).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (listenerOk && shown.isEmpty()) {
                    Text(
                        when (mode) {
                            Mode.App -> "Brak wiadomości z tej aplikacji z ostatnich 24 h. Wpisz, co zrobić, poniżej."
                            Mode.Shade -> "Pasek powiadomień jest pusty."
                            Mode.All -> "Brak powiadomień z ostatnich 24 h."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (shown.isNotEmpty()) {
                    LazyColumn(
                        Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(shown, key = { it.key }) { message ->
                            RecentRow(
                                message = message,
                                selected = message.key == selected?.key,
                                onClick = {
                                    selected = if (message.key == selected?.key) null else message
                                    if (!edited) text = suggestedText(selected)
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        edited = true
                    },
                    label = { Text("Co zrobić?") },
                    placeholder = { Text("np. Odpisać o sobotę") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = due == today, onClick = { due = today }, label = { Text("Dziś") })
                    FilterChip(selected = due == tomorrow, onClick = { due = tomorrow }, label = { Text("Jutro") })
                    FilterChip(
                        selected = due.isAfter(tomorrow),
                        onClick = { pickDate(context, due) { due = it } },
                        label = { Text(if (due.isAfter(tomorrow)) dayLabel(due, today) else "Inny dzień…") },
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("Anuluj") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { save() }, enabled = text.isNotBlank()) { Text("Złap") }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(message: RecentMessage, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                listOfNotNull(message.appName.ifEmpty { null }, message.sender).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            message.text?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
