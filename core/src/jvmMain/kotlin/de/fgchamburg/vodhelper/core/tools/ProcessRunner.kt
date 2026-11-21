package de.fgchamburg.vodhelper.core.tools

import kotlin.time.Duration

/** exitCode null = timed out and was stopped. output = stdout and stderr merged. */
data class ProcessResult(val exitCode: Int?, val output: String)

/** Runs an outside program. Tests use a fake. */
fun interface ProcessRunner {
    /** Throws IOException if the program can't start. */
    fun run(command: List<String>, timeout: Duration): ProcessResult
}
