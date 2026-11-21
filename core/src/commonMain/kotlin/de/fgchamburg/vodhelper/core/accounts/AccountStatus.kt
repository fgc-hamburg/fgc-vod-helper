package de.fgchamburg.vodhelper.core.accounts

sealed interface AccountStatus {
    /** This build of the app has no keys for the service. */
    data object NotConfigured : AccountStatus
    data object NotConnected : AccountStatus
    data class Connected(val accountName: String) : AccountStatus

    /** Signed in before, but the service took access away. The user must sign in again. */
    data class NeedsReconnect(val accountName: String) : AccountStatus
}
