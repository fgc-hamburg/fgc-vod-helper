package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.accounts.AccountAction
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport

@Composable
fun SettingsScreen(
    info: AppInfo,
    statuses: Map<AccountKind, AccountStatus>?,
    tools: VideoToolsReport?,
    onAction: (AccountKind, AccountAction) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.align(Alignment.Start)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        AccountsSection(statuses, onAction)
        VideoToolsSection(tools)
        Text(info.versionLabel, style = MaterialTheme.typography.bodyMedium)
    }
}
