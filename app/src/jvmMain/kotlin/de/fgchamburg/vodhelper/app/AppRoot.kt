package de.fgchamburg.vodhelper.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.accounts.Account
import de.fgchamburg.vodhelper.core.accounts.AccountAction
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import de.fgchamburg.vodhelper.core.settings.SettingsStore
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { SETUP, HOME, SETTINGS }

private const val NEW_PROJECT_NOTICE = "Creating projects comes in a later update."

/** Holds navigation and account state; the screens themselves are stateless. */
@Composable
fun AppRoot(info: AppInfo, tools: VideoToolsReport?, accounts: List<Account>, settings: SettingsStore) {
    var screen by remember { mutableStateOf(if (settings.load().setupDone) Screen.HOME else Screen.SETUP) }
    var statuses by remember { mutableStateOf<Map<AccountKind, AccountStatus>?>(null) }
    var refreshCount by remember { mutableStateOf(0) }
    var connecting by remember { mutableStateOf<AccountKind?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Keychain reads can block, so they stay off the UI thread.
    LaunchedEffect(refreshCount) {
        statuses = withContext(Dispatchers.IO) { accounts.associate { it.kind to it.status() } }
    }

    val onAction: (AccountKind, AccountAction) -> Unit = { kind, action ->
        when (action) {
            AccountAction.CONNECT, AccountAction.RECONNECT -> connecting = kind
            AccountAction.SIGN_OUT -> scope.launch {
                withContext(Dispatchers.IO) { accounts.first { it.kind == kind }.signOut() }
                refreshCount++
            }
        }
    }

    AppTheme {
        when (screen) {
            Screen.SETUP -> SetupScreen(
                statuses,
                onAction,
                onContinue = {
                    settings.save(settings.load().copy(setupDone = true))
                    screen = Screen.HOME
                },
            )
            Screen.HOME -> HomeScreen(
                info,
                statuses,
                onNewProject = { notice = NEW_PROJECT_NOTICE },
                onOpenSettings = { screen = Screen.SETTINGS },
                notice = notice,
            )
            Screen.SETTINGS -> SettingsScreen(info, statuses, tools, onAction, onBack = { screen = Screen.HOME })
        }
        connecting?.let { kind ->
            ConnectDialog(kind) {
                connecting = null
                refreshCount++
            }
        }
    }
}
