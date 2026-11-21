package de.fgchamburg.vodhelper.core.accounts

import kotlinx.serialization.Serializable

@Serializable
data class AccountCredential(
    val accountName: String,
    val secret: String,
    val refreshSecret: String? = null,
    val accountId: String? = null,
    val expiresAtEpochSeconds: Long? = null,
    val needsReconnect: Boolean = false,
) {
    // never print secrets
    override fun toString() =
        "AccountCredential(accountName=$accountName, accountId=$accountId, " +
            "expiresAtEpochSeconds=$expiresAtEpochSeconds, needsReconnect=$needsReconnect, secret=***)"
}
