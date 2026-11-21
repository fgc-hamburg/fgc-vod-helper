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
        val code = runCheckTools(VideoToolsReport(ready), out = lines::add)

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
            out = lines::add,
        )

        assertEquals(1, code)
        assertTrue(lines.any { it.startsWith("yt-dlp: Missing") })
    }

    @Test
    fun `report path is read from the argument`() {
        assertEquals(null, checkToolsReportPath(arrayOf("--check-tools")))
        assertEquals(
            java.io.File("/tmp/r.txt"),
            checkToolsReportPath(arrayOf("--check-tools", "--check-tools-report=/tmp/r.txt")),
        )
        assertEquals(null, checkToolsReportPath(arrayOf("--check-tools-report=")))
    }

    @Test
    fun `summary lines are also written to the report file`() {
        val file = java.io.File.createTempFile("check-tools", ".txt")
        try {
            runCheckTools(VideoToolsReport(ready), file) {}
            assertEquals(
                "ffmpeg: 8.1.2\nffprobe: 8.1.2\nyt-dlp: 2026.10.26\nVLC engine: 3.0.24\n",
                file.readText(Charsets.UTF_8),
            )
        } finally {
            file.delete()
        }
    }
}
