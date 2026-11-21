package de.fgchamburg.vodhelper.core.accounts

import de.fgchamburg.vodhelper.core.AppInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountsTest {
    private fun statuses(info: AppInfo) = Accounts.create(info, InMemorySecretStore()).associate { it.kind to it.status() }

    @Test
    fun `creates all three accounts in order`() {
        val accounts = Accounts.create(AppInfo.from("1.0.0"), InMemorySecretStore())
        assertEquals(listOf(AccountKind.TWITCH, AccountKind.STARTGG, AccountKind.YOUTUBE), accounts.map { it.kind })
    }

    @Test
    fun `twitch is not configured without its client id`() {
        assertEquals(AccountStatus.NotConfigured, statuses(AppInfo.from("1.0.0"))[AccountKind.TWITCH])
        assertEquals(AccountStatus.NotConnected, statuses(AppInfo.from("1.0.0", twitchClientId = "id"))[AccountKind.TWITCH])
    }

    @Test
    fun `youtube is not configured without google keys`() {
        assertEquals(AccountStatus.NotConfigured, statuses(AppInfo.from("1.0.0", googleClientId = "id"))[AccountKind.YOUTUBE])
        assertEquals(
            AccountStatus.NotConnected,
            statuses(AppInfo.from("1.0.0", googleClientId = "id", googleClientSecret = "s"))[AccountKind.YOUTUBE],
        )
    }

    @Test
    fun `start gg is always configured`() {
        assertEquals(AccountStatus.NotConnected, statuses(AppInfo.from("1.0.0"))[AccountKind.STARTGG])
    }
}
