package de.fgchamburg.vodhelper.core.project

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectSessionTest {
    private class CountingStore(private val inner: InMemoryProjectStore) : ProjectStore by inner {
        var saves = 0
        override fun save(folder: String, project: Project) {
            saves++
            inner.save(folder, project)
        }
    }

    private val folder = "/p/Weekly"
    private val original = Project(name = "Weekly")

    private fun storeWithProject() = InMemoryProjectStore().also { it.projects[folder] = original }

    @Test
    fun `every change is saved`() {
        val store = storeWithProject()
        val session = ProjectSession(store, folder, original)
        val state = session.update { it.copy(name = "Renamed") }
        assertEquals(SaveState.Saved, state)
        assertEquals(SaveState.Saved, session.saveState)
        assertEquals("Renamed", store.projects.getValue(folder).name)
    }

    @Test
    fun `an unchanged update does not write`() {
        val counting = CountingStore(storeWithProject())
        val session = ProjectSession(counting, folder, original)
        assertEquals(SaveState.Saved, session.update { it })
        assertEquals(0, counting.saves)
    }

    @Test
    fun `a failed save is reported and the change is kept`() {
        val store = storeWithProject()
        val session = ProjectSession(store, folder, original)
        store.failSaves = true
        val state = session.update { it.copy(name = "Renamed") }
        assertEquals(SaveState.Failed("disk full"), state)
        assertEquals(state, session.saveState)
        assertEquals("Renamed", session.project.name)
        assertEquals("Weekly", store.projects.getValue(folder).name)
    }

    @Test
    fun `the next change retries a failed save`() {
        val store = storeWithProject()
        val session = ProjectSession(store, folder, original)
        store.failSaves = true
        session.update { it.copy(name = "Renamed") }
        store.failSaves = false
        assertEquals(SaveState.Saved, session.update { it })
        assertEquals("Renamed", store.projects.getValue(folder).name)
        session.update { it.copy(name = "Latest") }
        assertEquals("Latest", store.projects.getValue(folder).name)
    }
}
