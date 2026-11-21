package de.fgchamburg.vodhelper.core.accounts

import com.github.javakeyring.Keyring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Talks to the real Keychain / Credential Manager. Uses a test service name and always cleans up. */
class KeyringSecretStoreTest {
    private val service = "FGC VOD Helper (test)"

    private fun passwordStoreAvailable(): Boolean = try {
        Keyring.create().close()
        true
    } catch (e: Exception) {
        println("Skipping: no OS password store here (${e.javaClass.simpleName})")
        false
    }

    private fun uniqueKey() = "test-" + System.nanoTime()

    @Test
    fun `round trip`() {
        if (!passwordStoreAvailable()) return
        val store = KeyringSecretStore(service)
        val key = uniqueKey()
        try {
            store.put(key, "value-1")
            assertEquals("value-1", store.get(key))
            store.put(key, "value-2")
            assertEquals("value-2", store.get(key))
            store.delete(key)
            assertNull(store.get(key))
        } finally {
            store.delete(key)
        }
    }

    @Test
    fun `get of an unknown key is null`() {
        if (!passwordStoreAvailable()) return
        assertNull(KeyringSecretStore(service).get(uniqueKey()))
    }

    @Test
    fun `delete of an unknown key does nothing`() {
        if (!passwordStoreAvailable()) return
        KeyringSecretStore(service).delete(uniqueKey())
    }
}
