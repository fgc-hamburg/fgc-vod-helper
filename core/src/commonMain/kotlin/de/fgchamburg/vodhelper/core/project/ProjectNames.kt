package de.fgchamburg.vodhelper.core.project

object ProjectNames {
    private const val FORBIDDEN = "<>:\"/\\|?*"
    private val RESERVED = setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }

    /** A name that is safe as a folder name on macOS and Windows, or null when nothing usable is left. */
    fun folderName(name: String): String? {
        val cleaned = name.trim()
            .map { if (it in FORBIDDEN || it.code < 0x20) '-' else it }
            .joinToString("")
            .trimEnd('.', ' ')
        if (cleaned.isEmpty()) return null
        val base = cleaned.substringBefore('.').uppercase()
        return if (base in RESERVED) "$cleaned-" else cleaned
    }
}
