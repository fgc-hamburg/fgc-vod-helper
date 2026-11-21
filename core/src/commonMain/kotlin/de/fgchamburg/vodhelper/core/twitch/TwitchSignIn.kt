package de.fgchamburg.vodhelper.core.twitch

import kotlin.coroutines.cancellation.CancellationException

/** One Twitch sign-in attempt: asks for a code, waits for approval, saves the account. */
class TwitchSignIn(private val account: TwitchAccount) {
    /** Runs one sign-in attempt, reporting each state. Cancel the coroutine to stop waiting. */
    suspend fun run(onState: (TwitchSignInState) -> Unit) {
        onState(TwitchSignInState.Starting)
        val code = when (val start = account.auth.startSignIn()) {
            is StartResult.Started -> start.code
            StartResult.Offline -> return onState(TwitchSignInState.Failed(TwitchMessages.OFFLINE))
            is StartResult.Failed -> return onState(failed(start.reason))
        }
        onState(TwitchSignInState.WaitingForApproval(code.userCode, code.verificationUri))
        when (val result = account.auth.awaitApproval(code)) {
            is ApprovalResult.Approved -> {
                try {
                    account.save(result.credential)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return onState(failed("couldn't save the sign-in: ${e.message}"))
                }
                onState(TwitchSignInState.Connected(result.credential.accountName))
            }
            ApprovalResult.CodeExpired -> onState(TwitchSignInState.Failed(TwitchMessages.CODE_EXPIRED))
            ApprovalResult.Denied -> onState(TwitchSignInState.Failed(TwitchMessages.DENIED))
            is ApprovalResult.Failed -> onState(failed(result.reason))
        }
    }

    private fun failed(reason: String) = TwitchSignInState.Failed(TwitchMessages.failed(reason))
}
