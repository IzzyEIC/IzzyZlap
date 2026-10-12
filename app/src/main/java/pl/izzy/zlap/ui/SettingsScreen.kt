@file:OptIn(ExperimentalMaterial3Api::class)

package pl.izzy.zlap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.izzy.zlap.app
import pl.izzy.zlap.capture.BubbleService
import pl.izzy.zlap.reminders.Reminders

private val INTERVALS = listOf(5, 10, 15, 20, 30, 60)

@Composable
fun SettingsScreen(onBack: () -> Unit, onSetup: () -> Unit) {
    val context = LocalContext.current
    val settings = context.app.settings
    val scope = rememberCoroutineScope()

    var start by remember { mutableIntStateOf(settings.startMinutes) }
    var end by remember { mutableIntStateOf(settings.endMinutes) }
    var interval by remember { mutableIntStateOf(settings.intervalMinutes) }
    var choosingInterval by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(false) }
    var bubble by remember { mutableStateOf(settings.bubbleEnabled) }

    fun changed() {
        scope.launch { Reminders.reschedule(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SettingRow(
                title = "Przypominaj od",
                value = formatMinutes(start),
                description = "Nowe rzeczy trafiają na dziś i od tej godziny zaczynam przypominać.",
            ) {
                pickTime(context, start) {
                    start = it
                    settings.startMinutes = it
                    changed()
                }
            }
            SettingRow(
                title = "Powtarzaj co",
                value = "$interval min",
                description = "Dopóki na liście coś zostało.",
            ) { choosingInterval = true }
            SettingRow(
                title = "Cisza od",
                value = formatMinutes(end),
                description = "Po tej godzinie milknę. Niezrobione rzeczy wracają następnego dnia.",
            ) {
                pickTime(context, end) {
                    end = it
                    settings.endMinutes = it
                    changed()
                }
            }
            if (end <= start) {
                Text(
                    "„Cisza od” jest wcześniej niż „Przypominaj od” – dostaniesz tylko jedno przypomnienie dziennie.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            SoundSettingRow()
            ListItem(
                headlineContent = { Text("Pływająca bańka") },
                supportingContent = { Text("Pomarańczowe kółko nad innymi aplikacjami – stuknij, żeby złapać.") },
                trailingContent = {
                    Switch(checked = bubble, onCheckedChange = {
                        bubble = it
                        settings.bubbleEnabled = it
                        BubbleService.sync(context)
                    })
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingRow(
                title = "Konfiguracja telefonu",
                value = "",
                description = "Uprawnienia, bez których przypomnienia mogą nie dotrzeć.",
                onClick = onSetup,
            )
            SettingRow(
                title = "Zgłoś błąd lub pomysł",
                value = "",
                description = "Wyślij uwagę prosto do repozytorium Złap na GitHubie.",
                onClick = { feedback = true },
            )
        }
    }

    if (feedback) FeedbackDialog(onDismiss = { feedback = false })

    if (choosingInterval) {
        AlertDialog(
            onDismissRequest = { choosingInterval = false },
            title = { Text("Powtarzaj co") },
            text = {
                Column {
                    INTERVALS.forEach { minutes ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = minutes == interval, onClick = {
                                    interval = minutes
                                    settings.intervalMinutes = minutes
                                    choosingInterval = false
                                    changed()
                                }),
                        ) {
                            RadioButton(selected = minutes == interval, onClick = null)
                            Text("$minutes min", modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingInterval = false }) { Text("Zamknij") } },
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, description: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
