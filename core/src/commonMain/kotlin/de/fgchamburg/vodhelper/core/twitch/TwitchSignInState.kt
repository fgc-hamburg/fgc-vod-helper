package de.fgchamburg.vodhelper.core.twitch

sealed interface TwitchSignInState {
    data object Starting : TwitchSignInState
    data class WaitingForApproval(val userCode: String, val verificationUri: String) : TwitchSignInState
    data class Connected(val accountName: String) : TwitchSignInState

    /** [message] is ready to show. */
    data class Failed(val message: String) : TwitchSignInState
}
