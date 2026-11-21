package de.fgchamburg.vodhelper.core.accounts

import kotlinx.serialization.json.Json

/** An account whose sign-in is kept as JSON in the [SecretStore] under the kind's secret key. */
class StoredAccount(
    override val kind: AccountKind,
    private val store: SecretStore,
    private val configured: Boolean,
) : Account {
    private val json = Json { ignoreUnknownKeys = true }

    override fun status(): AccountStatus {
        if (!configured) return AccountStatus.NotConfigured
        val credential = credential() ?: return AccountStatus.NotConnected
        return AccountStatus.Connected(credential.accountName)
    }

    override fun save(credential: AccountCredential) {
        store.put(kind.secretKey, json.encodeToString(AccountCredential.serializer(), credential))
    }

    override fun signOut() = store.delete(kind.secretKey)

    /** The stored sign-in, or null when there is none or it can't be read. */
    fun credential(): AccountCredential? {
        val raw = store.get(kind.secretKey) ?: return null
        return try {
            json.decodeFromString(AccountCredential.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}
