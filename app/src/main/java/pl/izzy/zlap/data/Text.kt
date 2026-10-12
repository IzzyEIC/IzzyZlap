package pl.izzy.zlap.data

/**
 * Niewidoczne znaki, którymi maile (zwłaszcza newslettery) wypełniają podgląd:
 * łączniki zerowej szerokości, miękkie dywizy, znaczniki kierunku tekstu itp.
 */
private val INVISIBLE = Regex("[\\u00AD\\u034F\\u061C\\u115F\\u1160\\u17B4\\u17B5\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\u3164\\uFEFF\\uFFA0]")
private val LINE_SEPARATORS = Regex("[\\u2028\\u2029\\u0085]")
private val SPACES = Regex("\\s+")

/** Usuwa niewidoczne znaki, puste linie i zbędne spacje (np. z podglądu maila w Gmailu). */
fun String.tidy(): String =
    replace(INVISIBLE, "")
        .replace(LINE_SEPARATORS, "\n")
        .lines()
        .map { it.replace(SPACES, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
