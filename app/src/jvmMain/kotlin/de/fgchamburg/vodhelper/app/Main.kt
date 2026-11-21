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
import de.fgchamburg.vodhelper.core.accounts.Accounts
import de.fgchamburg.vodhelper.core.accounts.KeyringSecretStore
import de.fgchamburg.vodhelper.core.SystemClock
import de.fgchamburg.vodhelper.core.http.JdkHttpClient
import de.fgchamburg.vodhelper.core.project.FileProjectStore
import de.fgchamburg.vodhelper.core.settings.AppDataDir
import de.fgchamburg.vodhelper.core.settings.FileSettingsStore
import de.fgchamburg.vodhelper.core.twitch.TwitchAuth
import de.fgchamburg.vodhelper.core.tools.VideoToolsChecker
import de.fgchamburg.vodhelper.core.tools.VideoToolsLayout
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

private fun checkTools(): VideoToolsReport = VideoToolsChecker(VideoToolsLayout.fromSystem()).check()

fun main(args: Array<String>) {
    // Handled before any Compose/AWT call so it works without a display.
    if (CHECK_TOOLS_FLAG in args) exitProcess(runCheckTools(checkTools(), checkToolsReportPath(args), ::println))

    val info = AppInfo.fromBuildConfig()
    val twitchAuth = if (info.twitchConfigured) TwitchAuth(info.twitchClientId, JdkHttpClient(), SystemClock) else null
    val accounts = Accounts.create(info, KeyringSecretStore(), twitchAuth)
    val settings = FileSettingsStore(AppDataDir.current())
    val projects = FileProjectStore()
    val saves = SaveQueue()
    // Quitting any way (window close, Cmd+Q, exitProcess) first lets queued saves finish.
    Runtime.getRuntime().addShutdownHook(Thread { saves.awaitIdle() })
    application {
        var tools by remember { mutableStateOf<VideoToolsReport?>(null) }
        LaunchedEffect(Unit) { tools = withContext(Dispatchers.IO) { checkTools() } }
        Window(
            onCloseRequest = {
                saves.awaitIdle()
                exitApplication()
            },
            title = "${info.name} ${info.version}",
            state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
        ) {
            AppRoot(info, tools, accounts, settings, projects, SystemFolderPicker, saves, SystemBrowserOpener)
        }
    }
}
