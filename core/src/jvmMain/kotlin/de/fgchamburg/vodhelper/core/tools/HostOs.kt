package de.fgchamburg.vodhelper.core.tools

/** The operating systems the app ships for. */
enum class HostOs {
    MAC,
    WINDOWS,
    ;

    companion object {
        /** "Mac OS X" -> MAC, "Windows 11" -> WINDOWS, anything else -> null. */
        fun from(osName: String): HostOs? = when {
            osName.startsWith("Mac", ignoreCase = true) -> MAC
            osName.startsWith("Windows", ignoreCase = true) -> WINDOWS
            else -> null
        }

        fun current(): HostOs? = from(System.getProperty("os.name") ?: "")
    }
}
