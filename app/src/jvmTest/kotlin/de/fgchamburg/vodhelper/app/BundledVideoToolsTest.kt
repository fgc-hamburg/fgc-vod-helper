package de.fgchamburg.vodhelper.app

import de.fgchamburg.vodhelper.core.tools.HostOs
import de.fgchamburg.vodhelper.core.tools.ToolMessages
import de.fgchamburg.vodhelper.core.tools.ToolStatus
import de.fgchamburg.vodhelper.core.tools.VideoTool
import de.fgchamburg.vodhelper.core.tools.VideoToolsChecker
import de.fgchamburg.vodhelper.core.tools.VideoToolsLayout
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs the real bundled tools from the build output. Skipped where nothing is bundled (e.g. Linux). */
class BundledVideoToolsTest {
    @Test
    fun `bundled tools run and report the pinned versions`() {
        val dir = System.getProperty("videotools.dir") ?: return
        val layout = VideoToolsLayout(Path.of(dir), HostOs.current()!!)

        val report = VideoToolsChecker(layout).check()

        val summary = ToolMessages.summaryLines(report).joinToString("\n")
        println(summary)
        val expected = mapOf(
            VideoTool.FFMPEG to System.getProperty("videotools.version.ffmpeg"),
            VideoTool.FFPROBE to System.getProperty("videotools.version.ffmpeg"),
            VideoTool.YT_DLP to System.getProperty("videotools.version.yt-dlp"),
            VideoTool.VLC to System.getProperty("videotools.version.vlc"),
        )
        for ((tool, version) in expected) {
            assertEquals(ToolStatus.Ready(version), report.statuses.getValue(tool), "${tool.label}:\n$summary")
        }
    }
}
