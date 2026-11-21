package de.fgchamburg.vodhelper.core.accounts

import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.twitch.TwitchAccount
import de.fgchamburg.vodhelper.core.twitch.TwitchAuth

object Accounts {
    /** All accounts, in [AccountKind] order. Twitch renews its sign-in itself when [twitchAuth] is given. */
    fun create(info: AppInfo, store: SecretStore, twitchAuth: TwitchAuth? = null): List<Account> =
        AccountKind.entries.map { kind ->
            val configured = when (kind) {
                AccountKind.TWITCH -> info.twitchConfigured
                AccountKind.STARTGG -> true
                AccountKind.YOUTUBE -> info.youtubeConfigured
            }
            val stored = StoredAccount(kind, store, configured)
            if (kind == AccountKind.TWITCH && twitchAuth != null) TwitchAccount(stored, twitchAuth) else stored
        }
}
