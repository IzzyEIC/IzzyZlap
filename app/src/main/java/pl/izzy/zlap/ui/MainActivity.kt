package pl.izzy.zlap.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import pl.izzy.zlap.capture.BubbleService
import pl.izzy.zlap.reminders.Reminders

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ZlapTheme { ZlapRoot() } }
    }

    override fun onResume() {
        super.onResume()
        // Na wszelki wypadek: alarm zawsze ustawiony, gdy otwierasz aplikację
        lifecycleScope.launch { Reminders.reschedule(this@MainActivity) }
        BubbleService.sync(this)
    }
}

private enum class Screen { List, Settings, Setup }

@Composable
private fun ZlapRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.List) }
    BackHandler(enabled = screen != Screen.List) { screen = Screen.List }

    when (screen) {
        Screen.List -> TaskListScreen(
            onSettings = { screen = Screen.Settings },
            onSetup = { screen = Screen.Setup },
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.List },
            onSetup = { screen = Screen.Setup },
        )
        Screen.Setup -> SetupScreen(onBack = { screen = Screen.List })
    }
}
