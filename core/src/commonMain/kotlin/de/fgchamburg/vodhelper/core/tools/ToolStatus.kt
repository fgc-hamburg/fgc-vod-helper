package de.fgchamburg.vodhelper.core.tools

/** Result of checking one video tool. */
sealed interface ToolStatus {
    data class Ready(val version: String) : ToolStatus
    data class Missing(val path: String) : ToolStatus
    data class Broken(val detail: String) : ToolStatus
}
