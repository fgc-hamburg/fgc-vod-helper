package de.fgchamburg.vodhelper.app

import de.fgchamburg.vodhelper.core.tools.ToolMessages
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport

const val CHECK_TOOLS_FLAG = "--check-tools"

/** Prints one line per tool; returns the process exit code (0 when every tool is ready, 1 otherwise). */
fun runCheckTools(report: VideoToolsReport, out: (String) -> Unit): Int {
    ToolMessages.summaryLines(report).forEach(out)
    return if (report.allReady) 0 else 1
}
