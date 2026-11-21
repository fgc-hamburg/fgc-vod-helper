package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.accounts.AccountAction
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import de.fgchamburg.vodhelper.core.tools.HostOs
import de.fgchamburg.vodhelper.core.tools.ToolStatus
import de.fgchamburg.vodhelper.core.tools.VideoTool
import de.fgchamburg.vodhelper.core.tools.VideoToolsChecker
import de.fgchamburg.vodhelper.core.tools.VideoToolsLayout
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test

/** Renders the screens to PNGs for PR descriptions. Only runs when `ui.snapshots.dir` is set (make ui-snapshots). */
@OptIn(ExperimentalTestApi::class)
class UiSnapshots {
    private val info = AppInfo.from(version = "1.0.0")

    private val pinned = mapOf(
        VideoTool.FFMPEG to ToolStatus.Ready("8.1.2"),
        VideoTool.FFPROBE to ToolStatus.Ready("8.1.2"),
        VideoTool.YT_DLP to ToolStatus.Ready("2026.10.26"),
        VideoTool.VLC to ToolStatus.Ready("3.0.24"),
    )

    private fun realOrFakeReport(): VideoToolsReport {
        val dir = System.getProperty("videotools.dir") ?: return VideoToolsReport(pinned)
        return VideoToolsChecker(VideoToolsLayout(Path.of(dir), HostOs.current()!!)).check()
    }

    private val none = AccountKind.entries.associateWith<AccountKind, AccountStatus> { AccountStatus.NotConnected }
    private val ready = none + mapOf(
        AccountKind.TWITCH to AccountStatus.Connected("fgchamburg"),
        AccountKind.STARTGG to AccountStatus.Connected("FGC Hamburg"),
    )
    private val all = ready + (AccountKind.YOUTUBE to AccountStatus.Connected("FGC Hamburg"))
    private val missingStartgg = ready + (AccountKind.STARTGG to AccountStatus.NotConnected)

    private fun snapshot(outDir: File, name: String, content: @Composable () -> Unit) = runComposeUiTest {
        setContent {
            Box(Modifier.size(1100.dp, 720.dp)) { AppTheme(content) }
        }
        val image = onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage()
        ImageIO.write(image, "png", File(outDir, name))
    }

    @Test
    fun `render setup and settings screens`() {
        val outDir = System.getProperty("ui.snapshots.dir")?.let(::File) ?: return
        outDir.mkdirs()
        val noAction: (AccountKind, AccountAction) -> Unit = { _, _ -> }

        snapshot(outDir, "01-setup-first-launch.png") { SetupScreen(none, noAction, {}) }
        snapshot(outDir, "02-setup-ready.png") { SetupScreen(ready, noAction, {}) }
        snapshot(outDir, "03-connect-dialog.png") {
            SetupScreen(none, noAction, {})
            ConnectDialog(AccountKind.TWITCH) {}
        }
        snapshot(outDir, "04-home-missing-account.png") { HomeScreen(info, missingStartgg, {}, {}, notice = null) }
        snapshot(outDir, "05-settings.png") { SettingsScreen(info, all, realOrFakeReport(), noAction, {}) }
    }
}
