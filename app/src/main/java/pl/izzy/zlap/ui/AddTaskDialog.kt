package pl.izzy.zlap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onSave: (String, LocalDate) -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val tomorrow = remember { today.plusDays(1) }
    var text by rememberSaveable { mutableStateOf("") }
    var due by remember { mutableStateOf(today) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val save: () -> Unit = { if (text.isNotBlank()) onSave(text, due) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Co trzeba zrobić?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("np. Odpisać Kasi o sobotę") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
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
            }
        },
        confirmButton = { TextButton(onClick = save, enabled = text.isNotBlank()) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
