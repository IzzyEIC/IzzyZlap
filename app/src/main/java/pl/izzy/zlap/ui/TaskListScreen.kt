@file:OptIn(ExperimentalMaterial3Api::class)

package pl.izzy.zlap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.izzy.zlap.app
import pl.izzy.zlap.data.Task
import pl.izzy.zlap.setup.PhoneSetup
import java.time.LocalDate

@Composable
fun TaskListScreen(onSettings: () -> Unit, onSetup: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.tasks
    val tasks by repo.open.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }

    val resumeTick = rememberResumeTick()
    val setupNeeded = remember(resumeTick) { PhoneSetup.needsAttention(context, context.app.settings) }
    val today = remember(resumeTick, tasks) { LocalDate.now() }

    fun withUndo(message: String, original: Task, action: suspend () -> Unit) {
        scope.launch {
            action()
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Cofnij", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) repo.restore(original)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Złap", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Ustawienia") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Dodaj") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (setupNeeded) {
                item(key = "setup") { SetupBanner(onSetup) }
            }
            if (tasks.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
            sections(tasks, today).forEach { (header, list) ->
                item(key = "h-$header") {
                    Text(
                        header,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (header == "Zaległe") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                items(list, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        today = today,
                        onDone = { withUndo("Zrobione ✓", task) { repo.complete(task) } },
                        onPostpone = {
                            val target = if (task.dueEpochDay > today.toEpochDay()) today else today.plusDays(1)
                            withUndo("Przeniesione na ${dayLabel(target, today)}", task) { repo.moveTo(task, target) }
                        },
                        onPick = {
                            pickDate(context, LocalDate.ofEpochDay(task.dueEpochDay).coerceAtLeast(today)) { day ->
                                withUndo("Przeniesione na ${dayLabel(day, today)}", task) { repo.moveTo(task, day) }
                            }
                        },
                        onDelete = { withUndo("Usunięte", task) { repo.delete(task) } },
                        onOpen = task.sourcePackage?.let { pkg ->
                            context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                                { context.startActivity(intent) }
                            }
                        },
                    )
                }
            }
        }
    }

    if (adding) {
        AddTaskDialog(
            onDismiss = { adding = false },
            onSave = { title, due ->
                adding = false
                scope.launch { repo.add(title, due) }
            },
        )
    }
}

private fun LocalDate.coerceAtLeast(min: LocalDate): LocalDate = if (isBefore(min)) min else this

private fun sections(tasks: List<Task>, today: LocalDate): List<Pair<String, List<Task>>> {
    val t = today.toEpochDay()
    return buildList {
        tasks.filter { it.dueEpochDay < t }.takeIf { it.isNotEmpty() }?.let { add("Zaległe" to it) }
        tasks.filter { it.dueEpochDay == t }.takeIf { it.isNotEmpty() }?.let { add("Dziś" to it) }
        tasks.filter { it.dueEpochDay > t }
            .groupBy { it.dueEpochDay }
            .toSortedMap()
            .forEach { (day, list) ->
                add(dayLabel(LocalDate.ofEpochDay(day), today).replaceFirstChar { it.uppercase() } to list)
            }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    today: LocalDate,
    onDone: () -> Unit,
    onPostpone: () -> Unit,
    onPick: () -> Unit,
    onDelete: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val due = LocalDate.ofEpochDay(task.dueEpochDay)
    val overdue = due.isBefore(today)
    val meta = listOfNotNull(
        task.sourceApp?.let { "z $it" },
        if (overdue) "termin: ${dayLabel(due, today)}" else null,
    ).joinToString(" · ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                FilledTonalButton(onClick = onDone) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Zrobione")
                }
                TextButton(onClick = onPostpone) {
                    Text(if (due.isAfter(today)) "Na dziś" else "Jutro")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPick) { Icon(Icons.Filled.DateRange, contentDescription = "Inny dzień") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Usuń") }
            }
        }
    }
}

@Composable
private fun SetupBanner(onSetup: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSetup),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Dokończ konfigurację telefonu",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Bez tego Xiaomi może przespać przypomnienia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pusto – nic do zrobienia 🎉", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Text(
            "Gdy coś wpadnie w trakcie dnia, dodaj to przyciskiem „Dodaj”. Przypomnę Ci wieczorem.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
