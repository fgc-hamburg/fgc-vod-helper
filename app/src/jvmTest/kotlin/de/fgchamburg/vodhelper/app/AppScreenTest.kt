package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.tools.ToolStatus
import de.fgchamburg.vodhelper.core.tools.VideoTool
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppScreenTest {
    private val info = AppInfo.from(version = "1.2.3")

    private fun readyStatuses(): Map<VideoTool, ToolStatus> = mapOf(
        VideoTool.FFMPEG to ToolStatus.Ready("8.1.2"),
        VideoTool.FFPROBE to ToolStatus.Ready("8.1.2"),
        VideoTool.YT_DLP to ToolStatus.Ready("2026.10.26"),
        VideoTool.VLC to ToolStatus.Ready("3.0.24"),
    )

    @Test
    fun `shows name and version`() = runComposeUiTest {
        setContent { AppScreen(info, tools = null) }

        onNodeWithText("FGC VOD Helper").assertExists()
        onNodeWithText("Version 1.2.3").assertExists()
    }

    @Test
    fun `shows checking while tools are checked`() = runComposeUiTest {
        setContent { AppScreen(info, tools = null) }

        onNodeWithText("Checking video tools…").assertExists()
    }

    @Test
    fun `shows video tool versions`() = runComposeUiTest {
        setContent { AppScreen(info, tools = VideoToolsReport(readyStatuses())) }

        onNodeWithText("Video tools").assertExists()
        onNodeWithText("ffmpeg").assertExists()
        onNodeWithText("yt-dlp").assertExists()
        onNodeWithText("VLC engine").assertExists()
        onNodeWithText("2026.10.26").assertExists()
        onNodeWithText("3.0.24").assertExists()
    }

    @Test
    fun `shows a missing tool`() = runComposeUiTest {
        val report = VideoToolsReport(readyStatuses() + (VideoTool.FFMPEG to ToolStatus.Missing("/x/ffmpeg")))
        setContent { AppScreen(info, tools = report) }

        onNodeWithText("Missing. Reinstall the app to fix this.").assertExists()
    }
}
