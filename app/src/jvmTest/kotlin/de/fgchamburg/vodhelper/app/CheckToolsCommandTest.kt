package de.fgchamburg.vodhelper.app

import de.fgchamburg.vodhelper.core.tools.ToolStatus
import de.fgchamburg.vodhelper.core.tools.VideoTool
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckToolsCommandTest {
    private val ready = mapOf(
        VideoTool.FFMPEG to ToolStatus.Ready("8.1.2"),
        VideoTool.FFPROBE to ToolStatus.Ready("8.1.2"),
        VideoTool.YT_DLP to ToolStatus.Ready("2026.10.26"),
        VideoTool.VLC to ToolStatus.Ready("3.0.24"),
    )

    @Test
    fun `all ready exits 0 and prints each tool`() {
        val lines = mutableListOf<String>()
        val code = runCheckTools(VideoToolsReport(ready), lines::add)

        assertEquals(0, code)
        assertEquals(
            listOf("ffmpeg: 8.1.2", "ffprobe: 8.1.2", "yt-dlp: 2026.10.26", "VLC engine: 3.0.24"),
            lines,
        )
    }

    @Test
    fun `a missing tool exits 1 and names it`() {
        val lines = mutableListOf<String>()
        val code = runCheckTools(
            VideoToolsReport(ready + (VideoTool.YT_DLP to ToolStatus.Missing("/x"))),
            lines::add,
        )

        assertEquals(1, code)
        assertTrue(lines.any { it.startsWith("yt-dlp: Missing") })
    }
}
