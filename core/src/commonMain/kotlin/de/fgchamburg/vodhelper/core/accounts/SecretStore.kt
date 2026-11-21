package de.fgchamburg.vodhelper.core.accounts

/** The computer's password store. Keys are short names like "twitch"; values are never logged. */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)

    /** No error when the key is absent. */
    fun delete(key: String)
}
