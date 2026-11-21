package de.fgchamburg.vodhelper.core.settings

interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}
