package de.fgchamburg.vodhelper.core.settings

/** A [SettingsStore] kept in memory, for tests. */
class InMemorySettingsStore(var current: AppSettings = AppSettings()) : SettingsStore {
    override fun load(): AppSettings = current
    override fun save(settings: AppSettings) { current = settings }
}
