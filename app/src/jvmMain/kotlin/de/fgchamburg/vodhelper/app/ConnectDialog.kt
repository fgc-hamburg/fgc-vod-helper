package de.fgchamburg.vodhelper.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.fgchamburg.vodhelper.core.accounts.Account
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountMessages
import de.fgchamburg.vodhelper.core.twitch.TwitchAccount
import de.fgchamburg.vodhelper.core.twitch.TwitchSignIn
import de.fgchamburg.vodhelper.core.twitch.TwitchSignInState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The sign-in dialog for [kind]. Twitch has its flow; the others are placeholders until their tickets. */
@Composable
fun ConnectDialog(kind: AccountKind, account: Account?, openBrowser: BrowserOpener, onClose: () -> Unit) {
    if (account is TwitchAccount) {
        TwitchConnect(account, openBrowser, onClose)
    } else {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(AccountMessages.connectTitle(kind)) },
            text = { Text(AccountMessages.connectNotAvailableYet(kind)) },
            confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        )
    }
}

@Composable
private fun TwitchConnect(account: TwitchAccount, openBrowser: BrowserOpener, onClose: () -> Unit) {
    var state by remember { mutableStateOf<TwitchSignInState>(TwitchSignInState.Starting) }
    var attempt by remember { mutableStateOf(0) }
    var openedForAttempt by remember { mutableStateOf(-1) }

    // Leaving the dialog cancels this coroutine, which stops polling.
    LaunchedEffect(attempt) {
        state = TwitchSignInState.Starting
        withContext(Dispatchers.IO) { TwitchSignIn(account).run { state = it } }
    }
    LaunchedEffect(state) {
        val current = state
        if (current is TwitchSignInState.WaitingForApproval && openedForAttempt != attempt) {
            openedForAttempt = attempt
            openBrowser.open(current.verificationUri)
        }
    }

    TwitchConnectDialog(
        state = state,
        onOpenBrowser = openBrowser::open,
        onTryAgain = { attempt++ },
        onClose = onClose,
    )
}
