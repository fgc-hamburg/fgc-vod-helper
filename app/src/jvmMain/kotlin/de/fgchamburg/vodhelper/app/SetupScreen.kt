package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.accounts.AccountAction
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountMessages
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import de.fgchamburg.vodhelper.core.accounts.AccountsOverview

@Composable
fun SetupScreen(
    statuses: Map<AccountKind, AccountStatus>?,
    onAction: (AccountKind, AccountAction) -> Unit,
    onContinue: () -> Unit,
) {
    val overview = statuses?.let(::AccountsOverview)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome to FGC VOD Helper", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Connect your accounts to get started. You can change them later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
        )
        AccountsSection(statuses, onAction)
        overview?.let(AccountMessages::startProjectHint)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        // Always possible: Home keeps New project disabled until the required accounts are connected.
        Button(onClick = onContinue) { Text("Continue") }
    }
}
