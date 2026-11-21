package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import de.fgchamburg.vodhelper.core.tools.ToolStatus
import de.fgchamburg.vodhelper.core.tools.VideoTool
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    private val info = AppInfo.from(version = "1.2.3")
    private val statuses = AccountKind.entries.associateWith<AccountKind, AccountStatus> { AccountStatus.NotConnected }

    private fun readyStatuses(): Map<VideoTool, ToolStatus> = mapOf(
        VideoTool.FFMPEG to ToolStatus.Ready("8.1.2"),
        VideoTool.FFPROBE to ToolStatus.Ready("8.1.2"),
        VideoTool.YT_DLP to ToolStatus.Ready("2026.10.26"),
        VideoTool.VLC to ToolStatus.Ready("3.0.24"),
    )

    @Test
    fun `shows accounts and video tools`() = runComposeUiTest {
        setContent { SettingsScreen(info, statuses, VideoToolsReport(readyStatuses()), { _, _ -> }, {}) }

        onNodeWithText("Settings").assertExists()
        onNodeWithText("Accounts").assertExists()
        onNodeWithText("Twitch").assertExists()
        onNodeWithText("Video tools").assertExists()
        onNodeWithText("Version 1.2.3").assertExists()
    }

    @Test
    fun `back calls back`() = runComposeUiTest {
        var calls = 0
        setContent { SettingsScreen(info, statuses, null, { _, _ -> }, { calls++ }) }

        onNodeWithText("Back").performClick()
        assertEquals(1, calls)
    }

    @Test
    fun `shows checking while tools are checked`() = runComposeUiTest {
        setContent { SettingsScreen(info, statuses, null, { _, _ -> }, {}) }

        onNodeWithText("Checking video tools…").assertExists()
    }

    @Test
    fun `shows video tool versions`() = runComposeUiTest {
        setContent { SettingsScreen(info, statuses, VideoToolsReport(readyStatuses()), { _, _ -> }, {}) }

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
        setContent { SettingsScreen(info, statuses, report, { _, _ -> }, {}) }

        onNodeWithText("Missing. Reinstall the app to fix this.").assertExists()
    }
}
