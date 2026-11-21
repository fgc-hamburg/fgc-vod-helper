package de.fgchamburg.vodhelper.core.accounts

import kotlinx.serialization.Serializable

@Serializable
data class AccountCredential(val accountName: String, val secret: String, val refreshSecret: String? = null) {
    override fun toString() = "AccountCredential(accountName=$accountName, secret=***)" // never print secrets
}
