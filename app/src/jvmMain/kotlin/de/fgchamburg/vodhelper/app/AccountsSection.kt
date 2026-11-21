package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.accounts.AccountAction
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountMessages
import de.fgchamburg.vodhelper.core.accounts.AccountStatus

/** The accounts list shared by Setup and Settings. `statuses == null` while they are being read. */
@Composable
fun AccountsSection(
    statuses: Map<AccountKind, AccountStatus>?,
    onAction: (AccountKind, AccountAction) -> Unit,
) {
    Column(modifier = Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(AccountMessages.ACCOUNTS_HEADING, style = MaterialTheme.typography.titleMedium)
        if (statuses == null) {
            Text(AccountMessages.CHECKING_ACCOUNTS, style = MaterialTheme.typography.bodyMedium)
        } else {
            for (kind in AccountKind.entries) {
                AccountRow(kind, statuses[kind] ?: AccountStatus.NotConnected, onAction)
            }
        }
    }
}

@Composable
private fun AccountRow(kind: AccountKind, status: AccountStatus, onAction: (AccountKind, AccountAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("account-${kind.secretKey}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(kind.label, style = MaterialTheme.typography.titleMedium)
            Text(AccountMessages.requirement(kind), style = MaterialTheme.typography.bodySmall)
            Text(
                AccountMessages.describe(status),
                style = MaterialTheme.typography.bodyMedium,
                color = if (kind.required && status !is AccountStatus.Connected) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (action in AccountMessages.actions(status)) {
                OutlinedButton(onClick = { onAction(kind, action) }) { Text(AccountMessages.actionLabel(action)) }
            }
        }
    }
}
