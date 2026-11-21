package de.fgchamburg.vodhelper.core.accounts

import de.fgchamburg.vodhelper.core.AppInfo

object Accounts {
    /** All accounts, in [AccountKind] order. */
    fun create(info: AppInfo, store: SecretStore): List<Account> = AccountKind.entries.map { kind ->
        val configured = when (kind) {
            AccountKind.TWITCH -> info.twitchConfigured
            AccountKind.STARTGG -> true
            AccountKind.YOUTUBE -> info.youtubeConfigured
        }
        StoredAccount(kind, store, configured)
    }
}
