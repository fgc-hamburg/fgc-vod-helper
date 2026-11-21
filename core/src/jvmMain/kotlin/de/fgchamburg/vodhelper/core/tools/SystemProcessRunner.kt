package de.fgchamburg.vodhelper.core.tools

import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class SystemProcessRunner : ProcessRunner {
    override fun run(command: List<String>, timeout: Duration): ProcessResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        // Read on a separate thread so a chatty process can't fill the pipe and block.
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().use { r ->
                val buffer = CharArray(4096)
                while (true) {
                    val n = r.read(buffer)
                    if (n < 0) break
                    synchronized(output) { output.appendRange(buffer, 0, n) }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        val finished = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor()
        }
        reader.join(2_000)
        val text = synchronized(output) { output.toString() }
        return ProcessResult(if (finished) process.exitValue() else null, text)
    }
}
