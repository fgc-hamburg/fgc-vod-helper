package de.fgchamburg.vodhelper.core.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountsOverviewTest {
    private val connected = AccountStatus.Connected("x")

    @Test
    fun `cannot start a project with nothing connected`() {
        val overview = AccountsOverview(emptyMap())
        assertEquals(listOf(AccountKind.TWITCH, AccountKind.STARTGG), overview.missingRequired)
        assertFalse(overview.canStartProject)
    }

    @Test
    fun `youtube is not required`() {
        val overview = AccountsOverview(
            mapOf(AccountKind.TWITCH to connected, AccountKind.STARTGG to connected, AccountKind.YOUTUBE to AccountStatus.NotConnected),
        )
        assertTrue(overview.missingRequired.isEmpty())
    }

    @Test
    fun `can start a project when twitch and start gg are connected`() {
        assertTrue(AccountsOverview(mapOf(AccountKind.TWITCH to connected, AccountKind.STARTGG to connected)).canStartProject)
    }

    @Test
    fun `a missing status counts as not connected`() {
        val overview = AccountsOverview(mapOf(AccountKind.TWITCH to connected))
        assertEquals(listOf(AccountKind.STARTGG), overview.missingRequired)
    }

    @Test
    fun `needs reconnect blocks starting a project`() {
        val overview = AccountsOverview(
            mapOf(AccountKind.TWITCH to AccountStatus.NeedsReconnect("x"), AccountKind.STARTGG to connected),
        )
        assertEquals(listOf(AccountKind.TWITCH), overview.missingRequired)
        assertFalse(overview.canStartProject)
    }
}
