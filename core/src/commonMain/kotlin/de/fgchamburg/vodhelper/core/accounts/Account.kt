package de.fgchamburg.vodhelper.core.accounts

interface Account {
    val kind: AccountKind
    fun status(): AccountStatus

    /** Stores a finished sign-in. Called by each service's sign-in flow. */
    fun save(credential: AccountCredential)
    fun signOut()
}
