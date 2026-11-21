package de.fgchamburg.vodhelper.core.tools

/** Plain-language texts about tool problems, shown to the user. */
object ToolMessages {
    const val FIX = "Reinstall the app to fix this."

    fun describe(status: ToolStatus): String = when (status) {
        is ToolStatus.Ready -> status.version
        is ToolStatus.Missing -> "Missing. $FIX"
        is ToolStatus.Broken -> "Doesn't work (${status.detail}). $FIX"
    }

    fun stepBlocked(tool: VideoTool, status: ToolStatus): String = when (status) {
        is ToolStatus.Ready -> throw IllegalArgumentException("${tool.label} is ready, so nothing is blocked")
        is ToolStatus.Missing -> "Can't continue: ${tool.label} is missing. $FIX"
        is ToolStatus.Broken -> "Can't continue: ${tool.label} doesn't work (${status.detail}). $FIX"
    }

    fun summaryLines(report: VideoToolsReport): List<String> =
        report.statuses.map { (tool, status) -> "${tool.label}: ${describe(status)}" }
}
