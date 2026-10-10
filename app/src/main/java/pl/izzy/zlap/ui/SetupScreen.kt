@file:OptIn(ExperimentalMaterial3Api::class)

package pl.izzy.zlap.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pl.izzy.zlap.app
import pl.izzy.zlap.reminders.Reminders
import pl.izzy.zlap.setup.PhoneSetup

@Composable
fun SetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = context.app.settings
    var manualRefresh by remember { mutableIntStateOf(0) }
    val tick = rememberResumeTick() + manualRefresh

    val notifOk = remember(tick) { PhoneSetup.notificationsOk(context) }
    val exactOk = remember(tick) { PhoneSetup.exactAlarmsOk(context) }
    val batteryOk = remember(tick) { PhoneSetup.batteryOk(context) }
    var autostart by remember { mutableStateOf(settings.autostartConfirmed) }
    var locked by remember { mutableStateOf(settings.lockConfirmed) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) PhoneSetup.openNotificationSettings(context)
        manualRefresh++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Konfiguracja telefonu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (PhoneSetup.isXiaomi) {
                    "Xiaomi lubi usypiać aplikacje w tle. Przejdź po kolei każdy krok – bez tego przypomnienia mogą nie dotrzeć albo zniknąć po restarcie."
                } else {
                    "Przejdź po kolei każdy krok, żeby przypomnienia docierały niezawodnie."
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Step(
                number = 1,
                title = "Powiadomienia",
                description = "Pozwól Złap wysyłać powiadomienia.",
                done = notifOk,
                actionLabel = "Włącz",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        PhoneSetup.openNotificationSettings(context)
                    }
                },
            )
            Step(
                number = 2,
                title = "Przypomnienia o dokładnej godzinie",
                description = "Żeby przypomnienie przyszło o 18:00, a nie „kiedyś koło 18”.",
                done = exactOk,
                actionLabel = "Zezwól",
                onAction = { PhoneSetup.openExactAlarmSettings(context) },
            )
            Step(
                number = 3,
                title = "Bateria bez ograniczeń",
                description = "Wybierz „Zezwól” / „Bez ograniczeń”, żeby system nie usypiał Złap.",
                done = batteryOk,
                actionLabel = "Ustaw",
                onAction = { PhoneSetup.requestBatteryExemption(context) },
            )
            if (PhoneSetup.isXiaomi) {
                Step(
                    number = 4,
                    title = "Autostart",
                    description = "Włącz przełącznik przy „Złap”. Dzięki temu przypomnienia wrócą po restarcie telefonu. Potem zaznacz, że gotowe.",
                    done = autostart,
                    actionLabel = "Otwórz",
                    onAction = { PhoneSetup.openAutostart(context) },
                    onConfirm = {
                        autostart = it
                        settings.autostartConfirmed = it
                    },
                )
                Step(
                    number = 5,
                    title = "Kłódka w ostatnich aplikacjach",
                    description = "Otwórz listę ostatnich aplikacji, przytrzymaj kafelek Złap i stuknij kłódkę. Wtedy czyszczenie pamięci jej nie zamknie.",
                    done = locked,
                    onConfirm = {
                        locked = it
                        settings.lockConfirmed = it
                    },
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sprawdź, czy działa", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Stuknij przycisk, zablokuj telefon i odłóż go na minutę. Powinno przyjść powiadomienie „Złap działa ✓”.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = {
                        Reminders.scheduleTest(context)
                        Toast.makeText(context, "Zablokuj telefon i poczekaj minutę", Toast.LENGTH_LONG).show()
                    }) { Text("Próbne przypomnienie za 1 minutę") }
                }
            }
        }
    }
}

@Composable
private fun Step(
    number: Int,
    title: String,
    description: String,
    done: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onConfirm: ((Boolean) -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (done) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Gotowe", tint = Color_Ok)
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (actionLabel != null && onAction != null && (!done || onConfirm != null)) {
                    FilledTonalButton(onClick = onAction) { Text(actionLabel) }
                }
                if (onConfirm != null) {
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = done, onCheckedChange = onConfirm)
                    Text("Zrobione", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private val Color_Ok = androidx.compose.ui.graphics.Color(0xFF2F9E44)
