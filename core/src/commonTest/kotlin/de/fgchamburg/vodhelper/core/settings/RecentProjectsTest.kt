package de.fgchamburg.vodhelper.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentProjectsTest {
    private fun entry(n: Int) = RecentProject("/p/$n", "Project $n")

    @Test
    fun `opened puts the project first`() {
        assertEquals(listOf(entry(2), entry(1)), RecentProjects.opened(listOf(entry(1)), entry(2)))
    }

    @Test
    fun `opening again moves it to the top without duplicates`() {
        val list = listOf(entry(3), entry(2), entry(1))
        assertEquals(listOf(entry(1), entry(3), entry(2)), RecentProjects.opened(list, entry(1)))
    }

    @Test
    fun `rename updates the name`() {
        val renamed = RecentProject("/p/1", "New name")
        assertEquals(listOf(renamed, entry(2)), RecentProjects.opened(listOf(entry(2), entry(1)), renamed))
    }

    @Test
    fun `keeps at most 10`() {
        val list = (1..11).fold(emptyList<RecentProject>()) { acc, n -> RecentProjects.opened(acc, entry(n)) }
        assertEquals(10, list.size)
        assertEquals(entry(11), list.first())
        assertEquals(entry(2), list.last())
    }

    @Test
    fun `removed drops only that folder`() {
        assertEquals(listOf(entry(2)), RecentProjects.removed(listOf(entry(1), entry(2)), "/p/1"))
    }
}
