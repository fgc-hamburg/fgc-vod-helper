package de.fgchamburg.vodhelper.core.tools

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Finds each bundled tool, runs it, and reports its version or what's wrong. One failure never stops the others. */
class VideoToolsChecker(
    private val layout: VideoToolsLayout?,
    private val runner: ProcessRunner = SystemProcessRunner(),
    private val vlcProbe: VlcEngineProbe? = layout?.let { JnaVlcEngineProbe() },
    // Generous: on macOS the first run of freshly installed files waits ~25 s for the system security check.
    private val timeout: Duration = 120.seconds,
) {
    fun check(): VideoToolsReport {
        if (layout == null) return VideoToolsReport.allMissing("video tools folder not found")
        return VideoToolsReport(
            mapOf(
                VideoTool.FFMPEG to checkProgram(layout.executable(VideoTool.FFMPEG), "-version", ToolVersionParser::ffmpeg),
                VideoTool.FFPROBE to checkProgram(layout.executable(VideoTool.FFPROBE), "-version", ToolVersionParser::ffprobe),
                VideoTool.YT_DLP to checkProgram(layout.executable(VideoTool.YT_DLP), "--version", ToolVersionParser::ytDlp),
                VideoTool.VLC to checkVlc(layout),
            ),
        )
    }

    private fun checkProgram(path: Path, flag: String, parse: (String) -> String?): ToolStatus {
        if (!Files.exists(path)) return ToolStatus.Missing(path.toString())
        val result = try {
            runner.run(listOf(path.toString(), flag), timeout)
        } catch (e: IOException) {
            return ToolStatus.Broken("couldn't start: ${e.message}")
        }
        return when {
            result.exitCode == null -> ToolStatus.Broken("didn't answer within ${timeout.inWholeSeconds} s")
            result.exitCode != 0 -> ToolStatus.Broken("stopped with error code ${result.exitCode}")
            else -> parse(result.output)?.let { ToolStatus.Ready(it) } ?: ToolStatus.Broken(UNEXPECTED_OUTPUT)
        }
    }

    private fun checkVlc(layout: VideoToolsLayout): ToolStatus {
        // Path is Iterable<Path>, so `+` would add its name parts; build the list explicitly.
        (layout.vlcLibraries + listOf(layout.vlcPluginDir)).firstOrNull { !Files.exists(it) }
            ?.let { return ToolStatus.Missing(it.toString()) }
        val raw = try {
            checkNotNull(vlcProbe) { "no VLC probe" }.rawVersion(layout.vlcLibraries)
        } catch (t: Throwable) {
            return ToolStatus.Broken("couldn't load: ${t.message ?: t::class.simpleName}")
        }
        return ToolVersionParser.vlc(raw)?.let { ToolStatus.Ready(it) } ?: ToolStatus.Broken(UNEXPECTED_OUTPUT)
    }

    private companion object {
        const val UNEXPECTED_OUTPUT = "unexpected version output"
    }
}
