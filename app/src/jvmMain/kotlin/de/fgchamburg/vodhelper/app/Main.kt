package de.fgchamburg.vodhelper.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.fgchamburg.vodhelper.core.AppInfo

fun main() {
    val info = AppInfo.fromBuildConfig()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "${info.name} ${info.version}",
            state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
        ) {
            AppScreen(info)
        }
    }
}
