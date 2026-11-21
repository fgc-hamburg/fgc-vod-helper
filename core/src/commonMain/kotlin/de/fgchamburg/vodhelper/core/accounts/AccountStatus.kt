package de.fgchamburg.vodhelper.core.accounts

sealed interface AccountStatus {
    /** This build of the app has no keys for the service. */
    data object NotConfigured : AccountStatus
    data object NotConnected : AccountStatus
    data class Connected(val accountName: String) : AccountStatus
}
