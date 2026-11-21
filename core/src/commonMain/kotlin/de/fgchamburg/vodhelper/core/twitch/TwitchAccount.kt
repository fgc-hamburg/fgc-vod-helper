package de.fgchamburg.vodhelper.core.twitch

import de.fgchamburg.vodhelper.core.accounts.Account
import de.fgchamburg.vodhelper.core.accounts.AccountCredential
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import de.fgchamburg.vodhelper.core.accounts.StoredAccount
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface TokenResult {
    data class Token(val value: String) : TokenResult {
        // never print secrets
        override fun toString() = "Token(value=***)"
    }
    data object NotConnected : TokenResult
    data object AccessLost : TokenResult
    data object Offline : TokenResult
}

/** The Twitch account: a [StoredAccount] that renews its access token and notices when access is lost. */
class TwitchAccount(private val stored: StoredAccount, val auth: TwitchAuth) : Account by stored {
    companion object {
        const val REFRESH_MARGIN_SECONDS = 300L
    }

    // A rotated refresh token can be used only once, so refreshes never run in parallel.
    private val lock = Mutex()

    override suspend fun checkStatus(): AccountStatus = lock.withLock {
        val status = stored.status()
        if (status !is AccountStatus.Connected) return status
        val credential = stored.credential() ?: return AccountStatus.NotConnected
        if (isExpiring(credential)) return statusAfterRefresh(credential)
        when (auth.validate(credential.secret)) {
            ValidateResult.Valid, ValidateResult.Offline -> status
            ValidateResult.Invalid -> statusAfterRefresh(credential)
        }
    }

    /** A valid access token for an API call, renewed first when it is about to expire. */
    suspend fun accessToken(): TokenResult = lock.withLock {
        val credential = stored.credential() ?: return TokenResult.NotConnected
        if (credential.needsReconnect) return TokenResult.AccessLost
        if (!isExpiring(credential)) return TokenResult.Token(credential.secret)
        refreshToken(credential)
    }

    /** Renews the access token even when it looks fine, e.g. after Twitch answered an API call with 401. */
    suspend fun forceRefresh(): TokenResult = lock.withLock {
        val credential = stored.credential() ?: return TokenResult.NotConnected
        if (credential.needsReconnect) return TokenResult.AccessLost
        refreshToken(credential)
    }

    private fun isExpiring(credential: AccountCredential): Boolean {
        val expiresAt = credential.expiresAtEpochSeconds ?: return true
        return expiresAt <= auth.clock.nowEpochSeconds() + REFRESH_MARGIN_SECONDS
    }

    private suspend fun statusAfterRefresh(credential: AccountCredential): AccountStatus =
        when (refreshToken(credential)) {
            TokenResult.AccessLost -> AccountStatus.NeedsReconnect(credential.accountName)
            else -> AccountStatus.Connected(credential.accountName)
        }

    /** Must be called with [lock] held. Saves a rotated credential, or flags lost access. */
    private suspend fun refreshToken(credential: AccountCredential): TokenResult =
        when (val result = auth.refresh(credential)) {
            is RefreshResult.Refreshed -> {
                stored.save(result.credential)
                TokenResult.Token(result.credential.secret)
            }
            RefreshResult.AccessLost -> {
                stored.markNeedsReconnect()
                TokenResult.AccessLost
            }
            RefreshResult.Offline -> TokenResult.Offline
        }
}
