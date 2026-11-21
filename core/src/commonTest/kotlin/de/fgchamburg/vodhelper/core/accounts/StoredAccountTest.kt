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

    @Test
    fun `new credential fields round trip`() {
        val account = StoredAccount(AccountKind.TWITCH, InMemorySecretStore(), configured = true)
        val full = AccountCredential(
            "Renato", "access", "refresh",
            accountId = "123", expiresAtEpochSeconds = 1_000, needsReconnect = false,
        )
        account.save(full)
        assertEquals(full, account.credential())
    }

    @Test
    fun `old stored credential still loads`() {
        val store = InMemorySecretStore()
        store.put("twitch", """{"accountName":"a","secret":"s"}""")
        val loaded = StoredAccount(AccountKind.TWITCH, store, configured = true).credential()
        assertEquals(AccountCredential("a", "s", null, null, null, false), loaded)
    }

    @Test
    fun `needs reconnect flag shows needs reconnect`() {
        val account = StoredAccount(AccountKind.TWITCH, InMemorySecretStore(), configured = true)
        account.save(AccountCredential("a", "s", needsReconnect = true))
        assertEquals(AccountStatus.NeedsReconnect("a"), account.status())
    }

    @Test
    fun `markNeedsReconnect keeps the sign-in and sets the flag`() {
        val account = StoredAccount(AccountKind.TWITCH, InMemorySecretStore(), configured = true)
        account.save(AccountCredential("a", "s", "r", accountId = "1", expiresAtEpochSeconds = 5))
        account.markNeedsReconnect()
        assertEquals(AccountCredential("a", "s", "r", "1", 5, true), account.credential())
        assertEquals(AccountStatus.NeedsReconnect("a"), account.status())
    }

    @Test
    fun `markNeedsReconnect without a sign-in does nothing`() {
        val store = InMemorySecretStore()
        StoredAccount(AccountKind.TWITCH, store, configured = true).markNeedsReconnect()
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `toString hides the refresh secret and shows the new fields`() {
        val text = AccountCredential("a", "acc-secret", "ref-secret", "42", 99, true).toString()
        assertFalse("acc-secret" in text)
        assertFalse("ref-secret" in text)
        assertTrue("accountId=42" in text)
        assertTrue("expiresAtEpochSeconds=99" in text)
        assertTrue("needsReconnect=true" in text)
        assertTrue("secret=***" in text)
    }
}
