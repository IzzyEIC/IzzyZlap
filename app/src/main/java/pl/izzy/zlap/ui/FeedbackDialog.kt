package pl.izzy.zlap.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

private const val ISSUES_URL = "https://github.com/IzzyEIC/IzzyZlap/issues/new"

/** Zgłoszenie błędu lub pomysłu jako GitHub Issue – z dopisaną wersją aplikacji i telefonu. */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var isBug by rememberSaveable { mutableStateOf(true) }
    var text by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zgłoś uwagę") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isBug, onClick = { isBug = true }, label = { Text("🐞 Błąd") })
                    FilterChip(selected = !isBug, onClick = { isBug = false }, label = { Text("💡 Pomysł") })
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (isBug) "Co nie działa?" else "Co usprawnić?") },
                    placeholder = {
                        Text(if (isBug) "np. Po stuknięciu bańki w Messengerze nic się nie dzieje" else "np. Chcę móc edytować zadanie")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Otworzy się GitHub z gotowym zgłoszeniem – stuknij tam „Create” / „Submit new issue”. " +
                        "Możesz dołączyć zrzut ekranu. Repo jest publiczne, więc nie wpisuj prywatnych treści wiadomości.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    openIssue(context, isBug, text.trim())
                    onDismiss()
                },
            ) { Text("Dalej") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

private fun openIssue(context: Context, isBug: Boolean, text: String) {
    val firstLine = text.lineSequence().first()
    val title = (if (isBug) "Błąd: " else "Pomysł: ") + firstLine.take(70) + if (firstLine.length > 70) "…" else ""
    val body = buildString {
        appendLine(text)
        appendLine()
        appendLine("---")
        appendLine("Wersja: ${appVersion(context)}")
        appendLine("Telefon: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
    }
    val uri = Uri.parse(ISSUES_URL).buildUpon()
        .appendQueryParameter("title", title)
        .appendQueryParameter("body", body)
        .appendQueryParameter("labels", if (isBug) "bug" else "enhancement")
        .build()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Brak przeglądarki do otwarcia GitHuba", Toast.LENGTH_LONG).show()
    }
}

private fun appVersion(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "Złap ${info.versionName} (${info.longVersionCode})"
} catch (e: Exception) {
    "nieznana"
}
