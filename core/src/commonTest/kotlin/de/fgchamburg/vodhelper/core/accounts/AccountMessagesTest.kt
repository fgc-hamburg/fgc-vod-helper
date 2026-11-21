package de.fgchamburg.vodhelper.core.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountMessagesTest {
    private val connected = AccountStatus.Connected("Renato")

    @Test
    fun requirement() {
        assertEquals("Required", AccountMessages.requirement(AccountKind.TWITCH))
        assertEquals("Required", AccountMessages.requirement(AccountKind.STARTGG))
        assertEquals("Optional: only needed to upload to YouTube", AccountMessages.requirement(AccountKind.YOUTUBE))
    }

    @Test
    fun `describe each status`() {
        assertEquals("Connected as Renato", AccountMessages.describe(connected))
        assertEquals("Not connected", AccountMessages.describe(AccountStatus.NotConnected))
        assertEquals("Not available in this build of the app", AccountMessages.describe(AccountStatus.NotConfigured))
    }

    @Test
    fun `actions for each status`() {
        assertEquals(listOf(AccountAction.CONNECT), AccountMessages.actions(AccountStatus.NotConnected))
        assertEquals(listOf(AccountAction.RECONNECT, AccountAction.SIGN_OUT), AccountMessages.actions(connected))
        assertEquals(emptyList(), AccountMessages.actions(AccountStatus.NotConfigured))
        assertEquals("Connect", AccountMessages.actionLabel(AccountAction.CONNECT))
        assertEquals("Reconnect", AccountMessages.actionLabel(AccountAction.RECONNECT))
        assertEquals("Sign out", AccountMessages.actionLabel(AccountAction.SIGN_OUT))
    }

    @Test
    fun `hint names the missing accounts`() {
        assertEquals(
            "Connect start.gg to start a project.",
            AccountMessages.startProjectHint(AccountsOverview(mapOf(AccountKind.TWITCH to connected))),
        )
        assertEquals(
            "Connect Twitch and start.gg to start a project.",
            AccountMessages.startProjectHint(AccountsOverview(emptyMap())),
        )
    }

    @Test
    fun `no hint when ready`() {
        val ready = AccountsOverview(mapOf(AccountKind.TWITCH to connected, AccountKind.STARTGG to connected))
        assertNull(AccountMessages.startProjectHint(ready))
    }

    @Test
    fun `connect dialog texts`() {
        assertEquals("Connect Twitch", AccountMessages.connectTitle(AccountKind.TWITCH))
        assertEquals(
            "Signing in to Twitch will be added in a later update.",
            AccountMessages.connectNotAvailableYet(AccountKind.TWITCH),
        )
        assertEquals("Accounts", AccountMessages.ACCOUNTS_HEADING)
        assertEquals("Checking accounts…", AccountMessages.CHECKING_ACCOUNTS)
    }
}
