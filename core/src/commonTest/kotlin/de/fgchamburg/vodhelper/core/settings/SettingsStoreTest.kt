package de.fgchamburg.vodhelper.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreTest {
    @Test
    fun `update changes only what it is given and keeps the rest`() {
        val store = InMemorySettingsStore(AppSettings(setupDone = true, lastProjectsFolder = "/a"))
        val recent = listOf(RecentProject("/a/Weekly", "Weekly"))

        val result = store.update { it.copy(recentProjects = recent) }

        val expected = AppSettings(setupDone = true, recentProjects = recent, lastProjectsFolder = "/a")
        assertEquals(expected, result)
        assertEquals(expected, store.current)
    }
}
