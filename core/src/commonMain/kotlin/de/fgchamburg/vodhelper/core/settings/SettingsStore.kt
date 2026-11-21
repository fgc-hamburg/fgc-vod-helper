package de.fgchamburg.vodhelper.core.settings

interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

/** Reads the settings, applies [change] and saves the result. Returns what was saved. */
fun SettingsStore.update(change: (AppSettings) -> AppSettings): AppSettings {
    val next = change(load())
    save(next)
    return next
}
