package de.fgchamburg.vodhelper.core.settings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSettingsStoreTest {
    private fun tempDir(): Path = Files.createTempDirectory("settings-test")

    @Test
    fun `defaults when the file is missing`() =
        assertEquals(AppSettings(), FileSettingsStore(tempDir()).load())

    @Test
    fun `saves and loads setup done`() {
        val store = FileSettingsStore(tempDir().resolve("nested"))
        store.save(AppSettings(setupDone = true))
        assertEquals(AppSettings(setupDone = true), FileSettingsStore(store.file.parent).load())
    }

    @Test
    fun `defaults when the file is broken`() {
        val store = FileSettingsStore(tempDir())
        store.file.writeText("{not json")
        assertEquals(AppSettings(), store.load())
    }

    @Test
    fun `save leaves no temp files`() {
        val dir = tempDir()
        FileSettingsStore(dir).save(AppSettings(setupDone = true))
        assertEquals(listOf("settings.json"), dir.listDirectoryEntries().map { it.fileName.toString() })
    }

    @Test
    fun `saves and loads recent projects`() {
        val store = FileSettingsStore(tempDir())
        val settings = AppSettings(true, listOf(RecentProject("/a/Weekly", "Weekly")), "/a")
        store.save(settings)
        assertEquals(settings, FileSettingsStore(store.file.parent).load())
    }

    @Test
    fun `settings file holds no sign-in details`() {
        val store = FileSettingsStore(tempDir())
        store.save(AppSettings(setupDone = true))
        assertEquals("""{"setupDone":true}""", store.file.readText())
    }
}
