package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountMessages
import de.fgchamburg.vodhelper.core.twitch.TwitchMessages
import de.fgchamburg.vodhelper.core.twitch.TwitchSignInState

/** Renders the current Twitch sign-in state; all logic lives in core. */
@Composable
fun TwitchConnectDialog(
    state: TwitchSignInState,
    onOpenBrowser: (String) -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(AccountMessages.connectTitle(AccountKind.TWITCH)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    TwitchSignInState.Starting -> Text(TwitchMessages.STARTING)
                    is TwitchSignInState.WaitingForApproval -> {
                        Text(TwitchMessages.INSTRUCTIONS)
                        Text(
                            state.userCode,
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.testTag("twitch-code"),
                        )
                        OutlinedButton(onClick = { onOpenBrowser(state.verificationUri) }) {
                            Text(TwitchMessages.OPEN_BROWSER)
                        }
                        Text(TwitchMessages.WAITING, style = MaterialTheme.typography.bodySmall)
                    }
                    is TwitchSignInState.Connected -> Text(TwitchMessages.connected(state.accountName))
                    is TwitchSignInState.Failed -> Text(state.message)
                }
            }
        },
        confirmButton = {
            when (state) {
                is TwitchSignInState.Connected -> TextButton(onClick = onClose) { Text(TwitchMessages.DONE) }
                is TwitchSignInState.Failed -> TextButton(onClick = onTryAgain) { Text(TwitchMessages.TRY_AGAIN) }
                else -> TextButton(onClick = onClose) { Text(TwitchMessages.CANCEL) }
            }
        },
        dismissButton = {
            if (state is TwitchSignInState.Failed) TextButton(onClick = onClose) { Text(TwitchMessages.CANCEL) }
        },
    )
}
