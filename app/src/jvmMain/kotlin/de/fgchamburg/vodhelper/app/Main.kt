package de.fgchamburg.vodhelper.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.tools.VideoToolsChecker
import de.fgchamburg.vodhelper.core.tools.VideoToolsLayout
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

private fun checkTools(): VideoToolsReport = VideoToolsChecker(VideoToolsLayout.fromSystem()).check()

fun main(args: Array<String>) {
    // Handled before any Compose/AWT call so it works without a display.
    if (CHECK_TOOLS_FLAG in args) exitProcess(runCheckTools(checkTools(), ::println))

    val info = AppInfo.fromBuildConfig()
    application {
        var tools by remember { mutableStateOf<VideoToolsReport?>(null) }
        LaunchedEffect(Unit) { tools = withContext(Dispatchers.IO) { checkTools() } }
        Window(
            onCloseRequest = ::exitApplication,
            title = "${info.name} ${info.version}",
            state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
        ) {
            AppScreen(info, tools)
        }
    }
}
