package de.fgchamburg.vodhelper.core.settings

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Keeps [AppSettings] in `<dir>/settings.json`, written atomically so a crash can't leave half a file. */
class FileSettingsStore(private val dir: Path) : SettingsStore {
    private val json = Json { ignoreUnknownKeys = true }

    val file: Path get() = dir.resolve("settings.json")

    override fun load(): AppSettings = try {
        json.decodeFromString(AppSettings.serializer(), Files.readString(file))
    } catch (_: Exception) {
        AppSettings()
    }

    override fun save(settings: AppSettings) {
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, "settings", ".tmp")
        try {
            Files.writeString(tmp, json.encodeToString(AppSettings.serializer(), settings))
            Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
}
