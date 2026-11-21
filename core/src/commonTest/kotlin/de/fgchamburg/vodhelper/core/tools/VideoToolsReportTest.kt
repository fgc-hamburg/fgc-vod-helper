package de.fgchamburg.vodhelper.core.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoToolsReportTest {
    private fun ready(): Map<VideoTool, ToolStatus> =
        VideoTool.entries.associateWith { ToolStatus.Ready("1.0.0") }

    @Test
    fun `statuses follow tool order regardless of input order`() {
        val report = VideoToolsReport(ready().entries.reversed().associate { it.key to it.value })
        assertEquals(VideoTool.entries, report.statuses.keys.toList())
    }

    @Test
    fun `allReady only when every tool is ready`() {
        assertTrue(VideoToolsReport(ready()).allReady)
        assertFalse(VideoToolsReport(ready() + (VideoTool.VLC to ToolStatus.Broken("x"))).allReady)
        assertFalse(VideoToolsReport(ready() + (VideoTool.FFMPEG to ToolStatus.Missing("/p"))).allReady)
    }

    @Test
    fun `require returns the version`() {
        val report = VideoToolsReport(ready() + (VideoTool.FFMPEG to ToolStatus.Ready("8.1.2")))
        assertEquals("8.1.2", report.require(VideoTool.FFMPEG))
    }

    @Test
    fun `require throws with the tool name`() {
        val status = ToolStatus.Missing("/p/ffmpeg")
        val report = VideoToolsReport(ready() + (VideoTool.FFMPEG to status))
        val e = assertFailsWith<VideoToolUnavailableException> { report.require(VideoTool.FFMPEG) }
        assertEquals(ToolMessages.stepBlocked(VideoTool.FFMPEG, status), e.message)
        assertEquals(VideoTool.FFMPEG, e.tool)
        assertEquals(status, e.status)
    }

    @Test
    fun `allMissing marks every tool missing`() {
        val report = VideoToolsReport.allMissing("/app")
        assertFalse(report.allReady)
        assertEquals(VideoTool.entries.size, report.statuses.size)
        assertTrue(report.statuses.values.all { it is ToolStatus.Missing })
    }

    @Test
    fun `report must cover every tool`() {
        assertFailsWith<IllegalArgumentException> {
            VideoToolsReport(mapOf(VideoTool.FFMPEG to ToolStatus.Ready("1.0.0")))
        }
    }
}
