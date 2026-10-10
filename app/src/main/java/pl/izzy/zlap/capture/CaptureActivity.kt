package pl.izzy.zlap.capture

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
        val shared = sharedText(intent)
        setContent {
            ZlapTheme {
                CaptureSheet(
                    sharedText = shared,
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

        fun intent(context: Context): Intent =
            Intent(context, CaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

private const val PRESELECT_MAX_AGE_MS = 30 * 60 * 1000L

@Composable
private fun CaptureSheet(sharedText: String?, clipText: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recent by remember { RecentStore.items(context) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val listenerOk = remember { PhoneSetup.listenerOk(context) }
    val today = remember { LocalDate.now() }
    val tomorrow = remember { today.plusDays(1) }

    var selected by remember { mutableStateOf<RecentMessage?>(null) }
    var text by remember { mutableStateOf(sharedText.orEmpty()) }
    var edited by remember { mutableStateOf(sharedText != null) }
    var preselected by remember { mutableStateOf(false) }

    // Lista wczytuje się z bazy – po pierwszym wczytaniu zaznaczamy najświeższą wiadomość
    LaunchedEffect(recent) {
        if (!preselected && recent.isNotEmpty()) {
            preselected = true
            selected = recent.firstOrNull { System.currentTimeMillis() - it.postedAt < PRESELECT_MAX_AGE_MS }
            if (!edited) text = selected?.text.orEmpty()
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
                sourcePackage = source?.packageName,
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

                if (recent.isNotEmpty()) {
                    Text("Od kogo / skąd", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyColumn(
                        Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(recent, key = { it.key }) { message ->
                            RecentRow(
                                message = message,
                                selected = message.key == selected?.key,
                                onClick = {
                                    selected = if (message.key == selected?.key) null else message
                                    if (!edited) text = selected?.text.orEmpty()
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
                "${message.appName} · ${message.sender ?: "?"}",
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
