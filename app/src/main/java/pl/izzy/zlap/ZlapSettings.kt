package pl.izzy.zlap

import android.content.Context
import java.time.LocalTime

class ZlapSettings(context: Context) {
    private val prefs = context.getSharedPreferences("zlap", Context.MODE_PRIVATE)

    /** Od której przypominamy (minuty od północy). */
    var startMinutes: Int
        get() = prefs.getInt("start", 18 * 60)
        set(value) = prefs.edit().putInt("start", value).apply()

    /** Po której milkniemy do następnego dnia. */
    var endMinutes: Int
        get() = prefs.getInt("end", 21 * 60)
        set(value) = prefs.edit().putInt("end", value).apply()

    var intervalMinutes: Int
        get() = prefs.getInt("interval", 15)
        set(value) = prefs.edit().putInt("interval", value).apply()

    // Kroki konfiguracji MIUI, których system nie pozwala sprawdzić
    var autostartConfirmed: Boolean
        get() = prefs.getBoolean("autostart", false)
        set(value) = prefs.edit().putBoolean("autostart", value).apply()

    var lockConfirmed: Boolean
        get() = prefs.getBoolean("lock", false)
        set(value) = prefs.edit().putBoolean("lock", value).apply()

    /** Dźwięk przypomnienia; null = domyślny dźwięk powiadomień. */
    var soundUri: String?
        get() = prefs.getString("soundUri", null)
        set(value) = prefs.edit().putString("soundUri", value).apply()

    var soundName: String?
        get() = prefs.getString("soundName", null)
        set(value) = prefs.edit().putString("soundName", value).apply()

    /** Czy plik dźwięku zaimportowała Złap (wtedy usuwamy go przy zmianie). */
    var soundOwned: Boolean
        get() = prefs.getBoolean("soundOwned", false)
        set(value) = prefs.edit().putBoolean("soundOwned", value).apply()

    /** Dźwięku kanału nie da się zmienić po utworzeniu, więc każda zmiana tworzy nowy kanał. */
    var channelVersion: Int
        get() = prefs.getInt("channelVersion", 0)
        set(value) = prefs.edit().putInt("channelVersion", value).apply()

    var bubbleEnabled: Boolean
        get() = prefs.getBoolean("bubble", true)
        set(value) = prefs.edit().putBoolean("bubble", value).apply()

    var bubbleX: Int
        get() = prefs.getInt("bubbleX", 0)
        set(value) = prefs.edit().putInt("bubbleX", value).apply()

    var bubbleY: Int
        get() = prefs.getInt("bubbleY", 600)
        set(value) = prefs.edit().putInt("bubbleY", value).apply()

    /** MIUI: „Wyświetlaj wyskakujące okna w tle” – nie da się tego sprawdzić programowo. */
    var popupConfirmed: Boolean
        get() = prefs.getBoolean("popup", false)
        set(value) = prefs.edit().putBoolean("popup", value).apply()

    val startTime: LocalTime get() = LocalTime.of(startMinutes / 60, startMinutes % 60)
    val endTime: LocalTime get() = LocalTime.of(endMinutes / 60, endMinutes % 60)

    fun isInWindow(time: LocalTime): Boolean = !time.isBefore(startTime) && !time.isAfter(endTime)
}
