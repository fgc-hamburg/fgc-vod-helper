package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountMessages
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import de.fgchamburg.vodhelper.core.accounts.AccountsOverview

@Composable
fun HomeScreen(
    info: AppInfo,
    statuses: Map<AccountKind, AccountStatus>?,
    onNewProject: () -> Unit,
    onOpenSettings: () -> Unit,
    notice: String?,
) {
    val overview = statuses?.let(::AccountsOverview)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(info.name, style = MaterialTheme.typography.headlineLarge)
        Text(info.versionLabel, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNewProject, enabled = overview?.canStartProject == true) { Text("New project") }
            OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
        }
        overview?.let(AccountMessages::startProjectHint)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
