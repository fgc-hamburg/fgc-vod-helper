package de.fgchamburg.vodhelper.core.accounts

interface Account {
    val kind: AccountKind
    fun status(): AccountStatus

    /** Like [status], but may ask the service (renew, validate). Defaults to the local read. */
    suspend fun checkStatus(): AccountStatus = status()

    /** Stores a finished sign-in. Called by each service's sign-in flow. */
    fun save(credential: AccountCredential)
    fun signOut()
}
