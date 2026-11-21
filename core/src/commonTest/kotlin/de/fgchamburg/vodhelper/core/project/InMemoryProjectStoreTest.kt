package de.fgchamburg.vodhelper.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemoryProjectStoreTest {
    @Test fun `create then load`() {
        val store = InMemoryProjectStore()
        assertEquals(CreateResult.Created("/p/Weekly", Project(name = "Weekly")), store.create("/p", " Weekly "))
        assertEquals(LoadResult.Loaded(Project(name = "Weekly")), store.load("/p/Weekly"))
    }

    @Test fun `a second project with the same name is refused`() {
        val store = InMemoryProjectStore()
        store.create("/p", "Weekly")
        assertEquals(CreateResult.Refused(ProjectMessages.alreadyExists("Weekly")), store.create("/p", "Weekly"))
    }

    @Test fun `a blank name is refused`() =
        assertEquals(CreateResult.Refused(ProjectMessages.NAME_REQUIRED), InMemoryProjectStore().create("/p", "  "))

    @Test fun `an unknown folder is missing`() = assertEquals(LoadResult.Missing, InMemoryProjectStore().load("/nope"))

    @Test fun `save replaces the project`() {
        val store = InMemoryProjectStore()
        store.create("/p", "Weekly")
        store.save("/p/Weekly", Project(name = "Renamed"))
        assertEquals(LoadResult.Loaded(Project(name = "Renamed")), store.load("/p/Weekly"))
    }

    @Test fun `failSaves makes save throw`() {
        val store = InMemoryProjectStore().apply { failSaves = true }
        assertFailsWith<IllegalStateException> { store.save("/p/x", Project(name = "x")) }
    }
}
