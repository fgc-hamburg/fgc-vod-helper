package de.fgchamburg.vodhelper.core.accounts

/** A [SecretStore] kept in memory, for tests. */
class InMemorySecretStore : SecretStore {
    private val map = sortedMapOf<String, String>()
    val entries: Map<String, String> get() = map

    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
}
