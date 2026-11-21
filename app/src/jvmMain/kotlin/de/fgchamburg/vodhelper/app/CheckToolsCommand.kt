package de.fgchamburg.vodhelper.app

import de.fgchamburg.vodhelper.core.tools.ToolMessages
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import java.io.File

const val CHECK_TOOLS_FLAG = "--check-tools"
const val CHECK_TOOLS_REPORT_PREFIX = "--check-tools-report="

/** The file named by `--check-tools-report=<path>`, if given. The Windows launcher has no stdout, so scripts read this file. */
fun checkToolsReportPath(args: Array<String>): File? =
    args.firstOrNull { it.startsWith(CHECK_TOOLS_REPORT_PREFIX) }
        ?.removePrefix(CHECK_TOOLS_REPORT_PREFIX)
        ?.takeIf { it.isNotEmpty() }
        ?.let(::File)

/**
 * Prints one line per tool (and writes them to [reportFile] as UTF-8 when given);
 * returns the process exit code (0 when every tool is ready, 1 otherwise).
 */
fun runCheckTools(report: VideoToolsReport, reportFile: File? = null, out: (String) -> Unit): Int {
    val lines = ToolMessages.summaryLines(report)
    lines.forEach(out)
    reportFile?.writeText(lines.joinToString("") { it + "\n" }, Charsets.UTF_8)
    return if (report.allReady) 0 else 1
}
