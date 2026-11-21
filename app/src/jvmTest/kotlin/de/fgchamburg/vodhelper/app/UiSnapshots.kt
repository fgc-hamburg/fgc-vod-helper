package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.AppInfo
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

    private fun snapshot(outDir: File, name: String, tools: VideoToolsReport?) = runComposeUiTest {
        setContent {
            Box(Modifier.size(1100.dp, 720.dp)) { AppScreen(info, tools) }
        }
        val image = onRoot().captureToImage().toAwtImage()
        ImageIO.write(image, "png", File(outDir, name))
    }

    @Test
    fun `render video tools screens`() {
        val outDir = System.getProperty("ui.snapshots.dir")?.let(::File) ?: return
        outDir.mkdirs()

        snapshot(outDir, "01-video-tools-ready.png", realOrFakeReport())
        snapshot(outDir, "02-video-tools-checking.png", null)
        snapshot(
            outDir,
            "03-video-tools-missing.png",
            VideoToolsReport(
                pinned + mapOf(
                    VideoTool.FFMPEG to ToolStatus.Missing("/x/ffmpeg"),
                    VideoTool.VLC to ToolStatus.Broken("couldn't load: example"),
                ),
            ),
        )
    }
}
