package de.fgchamburg.vodhelper.core.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ToolMessagesTest {
    @Test
    fun `describe ready shows the version`() {
        assertEquals("8.1.2", ToolMessages.describe(ToolStatus.Ready("8.1.2")))
    }

    @Test
    fun `describe missing`() {
        assertEquals("Missing. Reinstall the app to fix this.", ToolMessages.describe(ToolStatus.Missing("/x")))
    }

    @Test
    fun `describe broken`() {
        assertEquals(
            "Doesn't work (stopped with error code 1). Reinstall the app to fix this.",
            ToolMessages.describe(ToolStatus.Broken("stopped with error code 1")),
        )
    }

    @Test
    fun `stepBlocked missing`() {
        assertEquals(
            "Can't continue: ffmpeg is missing. Reinstall the app to fix this.",
            ToolMessages.stepBlocked(VideoTool.FFMPEG, ToolStatus.Missing("/x")),
        )
    }

    @Test
    fun `stepBlocked broken`() {
        assertEquals(
            "Can't continue: VLC engine doesn't work (x). Reinstall the app to fix this.",
            ToolMessages.stepBlocked(VideoTool.VLC, ToolStatus.Broken("x")),
        )
    }

    @Test
    fun `stepBlocked rejects ready tools`() {
        assertFailsWith<IllegalArgumentException> {
            ToolMessages.stepBlocked(VideoTool.FFMPEG, ToolStatus.Ready("8.1.2"))
        }
    }

    @Test
    fun `summaryLines lists tools in order`() {
        val report = VideoToolsReport(
            mapOf(
                VideoTool.VLC to ToolStatus.Broken("x"),
                VideoTool.YT_DLP to ToolStatus.Ready("2026.10.26"),
                VideoTool.FFPROBE to ToolStatus.Missing("/p"),
                VideoTool.FFMPEG to ToolStatus.Ready("8.1.2"),
            ),
        )
        assertEquals(
            listOf(
                "ffmpeg: 8.1.2",
                "ffprobe: Missing. Reinstall the app to fix this.",
                "yt-dlp: 2026.10.26",
                "VLC engine: Doesn't work (x). Reinstall the app to fix this.",
            ),
            ToolMessages.summaryLines(report),
        )
    }
}
