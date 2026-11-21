package de.fgchamburg.vodhelper.core.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredAccountTest {
    private val credential = AccountCredential("Renato", "s3cret-token")

    @Test
    fun `not configured when the build has no keys`() {
        val account = StoredAccount(AccountKind.TWITCH, InMemorySecretStore(), configured = false)
        assertEquals(AccountStatus.NotConfigured, account.status())
    }

    @Test
    fun `not connected when nothing is stored`() {
        val account = StoredAccount(AccountKind.TWITCH, InMemorySecretStore(), configured = true)
        assertEquals(AccountStatus.NotConnected, account.status())
    }

    @Test
    fun `connected after save shows the account name`() {
        val account = StoredAccount(AccountKind.TWITCH, InMemorySecretStore(), configured = true)
        account.save(credential)
        assertEquals(AccountStatus.Connected("Renato"), account.status())
        assertEquals(credential, account.credential())
    }

    @Test
    fun `save writes only to the secret store`() {
        val store = InMemorySecretStore()
        StoredAccount(AccountKind.TWITCH, store, configured = true).save(credential)
        assertEquals(listOf("twitch"), store.entries.keys.toList())
        assertTrue("s3cret-token" in store.entries.getValue("twitch"))
    }

    @Test
    fun `sign out removes the stored sign-in`() {
        val store = InMemorySecretStore()
        val account = StoredAccount(AccountKind.TWITCH, store, configured = true)
        account.save(credential)
        account.signOut()
        assertEquals(AccountStatus.NotConnected, account.status())
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `unreadable entry counts as not connected`() {
        val store = InMemorySecretStore()
        store.put("twitch", "not json")
        val account = StoredAccount(AccountKind.TWITCH, store, configured = true)
        assertEquals(AccountStatus.NotConnected, account.status())
        assertEquals(null, account.credential())
    }

    @Test
    fun `credential toString hides the secret`() {
        val text = AccountCredential("Renato", "s3cret-token", "refresh-me").toString()
        assertFalse("s3cret-token" in text)
        assertFalse("refresh-me" in text)
        assertTrue("Renato" in text)
    }
}
