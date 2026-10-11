package pl.izzy.zlap.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import kotlinx.coroutines.launch
import pl.izzy.zlap.R
import pl.izzy.zlap.app
import pl.izzy.zlap.reminders.Notifier
import pl.izzy.zlap.reminders.SoundStore

private const val LONG_SOUND_SEC = 15

/** Własne dźwięki Złap (generowane przez tools/make_sounds.py). */
private val BUILT_IN_SOUNDS = listOf(
    Triple("Marimba", "Ciepłe „ding-dong” – przyjazne, polecane", R.raw.zlap_marimba),
    Triple("Kropla", "Dwa miękkie „bloop” – najdelikatniejsze", R.raw.zlap_kropla),
    Triple("Iskra", "Szybkie dzwoneczki – najbardziej zauważalne", R.raw.zlap_iskra),
)

@Composable
fun SoundSettingRow() {
    val context = LocalContext.current
    val settings = context.app.settings
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(settings.soundName) }
    var choosing by remember { mutableStateOf(false) }

    suspend fun setSound(uri: String?, label: String?, owned: Boolean) {
        val previous = settings.soundUri
        val previousOwned = settings.soundOwned
        settings.soundUri = uri
        settings.soundName = label
        settings.soundOwned = owned
        Notifier.applySound(context)
        if (previousOwned && previous != null && previous != uri) SoundStore.deleteOwned(context, previous)
        name = label
        Notifier.playSample(context)
    }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } ?: return@rememberLauncherForActivityResult
        val label = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
        scope.launch { setSound(uri.toString(), label, owned = false) }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = SoundStore.import(context, uri)
            if (imported == null) {
                Toast.makeText(context, "Nie udało się wczytać tego pliku", Toast.LENGTH_LONG).show()
                return@launch
            }
            setSound(imported.uri.toString(), imported.name, owned = true)
            val seconds = imported.durationSec ?: 0
            if (seconds > LONG_SOUND_SEC) {
                Toast.makeText(
                    context,
                    "Ten dźwięk trwa $seconds s – przypomnienie odegra go w całości. Lepiej sprawdza się coś krótkiego.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    ListItem(
        headlineContent = { Text("Dźwięk przypomnienia") },
        supportingContent = { Text("Stuknij, żeby wybrać lub odsłuchać.") },
        trailingContent = {
            Text(name ?: "Domyślny", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable { choosing = true },
    )

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Dźwięk przypomnienia") },
            text = {
                Column {
                    BUILT_IN_SOUNDS.forEach { (name, description, res) ->
                        SoundOption("♪ $name", description) {
                            choosing = false
                            scope.launch { setSound("android.resource://${context.packageName}/$res", name, owned = false) }
                        }
                    }
                    SoundOption("Dźwięki telefonu", "Lista dźwięków powiadomień") {
                        choosing = false
                        ringtonePicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Dźwięk przypomnienia")
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, settings.soundUri?.let(Uri::parse))
                        )
                    }
                    SoundOption("Własny plik z telefonu", "Np. mp3 – najlepiej krótki, 3–10 sekund") {
                        choosing = false
                        filePicker.launch(arrayOf("audio/*"))
                    }
                    SoundOption("Domyślny", "Standardowy dźwięk powiadomień") {
                        choosing = false
                        scope.launch { setSound(null, null, owned = false) }
                    }
                    SoundOption("▶ Zagraj teraz", "Odsłuchaj obecny dźwięk") {
                        Notifier.playSample(context)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Zamknij") } },
        )
    }
}

@Composable
private fun SoundOption(title: String, description: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
