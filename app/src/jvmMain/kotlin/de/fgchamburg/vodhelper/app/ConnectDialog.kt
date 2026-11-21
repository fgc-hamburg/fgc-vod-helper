package de.fgchamburg.vodhelper.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountMessages

/** Placeholder until each service ticket adds its sign-in flow. */
@Composable
fun ConnectDialog(kind: AccountKind, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(AccountMessages.connectTitle(kind)) },
        text = { Text(AccountMessages.connectNotAvailableYet(kind)) },
        confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
    )
}
