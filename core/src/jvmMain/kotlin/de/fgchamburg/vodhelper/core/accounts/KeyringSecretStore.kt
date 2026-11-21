package de.fgchamburg.vodhelper.core.accounts

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException

/**
 * The computer's password store: macOS Keychain or Windows Credential Manager.
 * Messages never contain the stored value.
 */
class KeyringSecretStore(private val service: String = SERVICE) : SecretStore {
    companion object { const val SERVICE = "FGC VOD Helper" }

    private val keyring: Keyring by lazy {
        try {
            Keyring.create()
        } catch (e: Exception) {
            throw SecretStoreException("The computer's password store is not available.", e)
        }
    }

    override fun get(key: String): String? = try {
        keyring.getPassword(service, key)
    } catch (_: PasswordAccessException) {
        null
    } catch (e: Exception) {
        throw SecretStoreException("Could not read '$key' from the password store.", e)
    }

    override fun put(key: String, value: String) {
        try {
            keyring.setPassword(service, key, value)
        } catch (e: Exception) {
            throw SecretStoreException("Could not save '$key' to the password store.", e)
        }
    }

    override fun delete(key: String) {
        try {
            keyring.deletePassword(service, key)
        } catch (_: PasswordAccessException) {
            // Already absent.
        } catch (e: Exception) {
            throw SecretStoreException("Could not remove '$key' from the password store.", e)
        }
    }
}

class SecretStoreException(message: String, cause: Throwable?) : Exception(message, cause)
