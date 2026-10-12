@file:OptIn(ExperimentalMaterial3Api::class)

package pl.izzy.zlap.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.filled.Build
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
import pl.izzy.zlap.capture.SourceOpener
import pl.izzy.zlap.data.Task
import pl.izzy.zlap.data.tidy
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
    var feedback by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { feedback = true }) { Icon(Icons.Filled.Build, contentDescription = "Zgłoś błąd lub pomysł") }
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
                        onOpen = if (SourceOpener.canOpen(task)) {
                            { SourceOpener.open(context, task) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (feedback) FeedbackDialog(onDismiss = { feedback = false })

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
        listOfNotNull(task.sourceApp, task.sender).joinToString(" · ").ifEmpty { null },
        if (overdue) "termin: ${dayLabel(due, today)}" else null,
    ).joinToString(" · ")

    // Po odhaczeniu chwilę pokazujemy przekreślenie, potem zadanie znika
    var checked by remember(task.id) { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (checked) {
            delay(450)
            onDone()
        }
    }
    val contentAlpha by animateFloatAsState(if (checked) 0.45f else 1f, label = "alpha")
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp)) {
            CheckCircle(
                checked = checked,
                overdue = overdue,
                onCheck = { checked = true },
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 12.dp, bottom = 8.dp)
                    .alpha(contentAlpha),
            ) {
                Text(
                    task.title.tidy(),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                )
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (onOpen != null) {
                    Text(
                        if (task.sender != null) "Otwórz rozmowę ↗" else "Otwórz ${task.sourceApp ?: "aplikację"} ↗",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpen)
                            .padding(vertical = 4.dp),
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Więcej",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (due.isAfter(today)) "Przenieś na dziś" else "Przenieś na jutro") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onPostpone()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Wybierz dzień…") },
                        leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onPick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Usuń") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Okrągłe pole do odhaczenia – puste kółko, po stuknięciu wypełnia się z ptaszkiem. */
@Composable
private fun CheckCircle(checked: Boolean, overdue: Boolean, onCheck: () -> Unit) {
    val ring = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(if (checked) ring else Color.Transparent, label = "fill")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = !checked, onClick = onCheck),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, ring, CircleShape),
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
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
            "Gdy coś wpadnie w trakcie dnia, stuknij pomarańczową bańkę albo „Dodaj”. Przypomnę Ci wieczorem.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
