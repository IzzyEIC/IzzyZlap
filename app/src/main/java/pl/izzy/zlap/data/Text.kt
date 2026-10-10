package pl.izzy.zlap.data

/** Usuwa puste linie i zbędne spacje (np. Gmail wstawia kilka pustych linii między tematem a treścią). */
fun String.tidy(): String =
    lines()
        .map { it.trim().replace(Regex("\s+"), " ") }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
