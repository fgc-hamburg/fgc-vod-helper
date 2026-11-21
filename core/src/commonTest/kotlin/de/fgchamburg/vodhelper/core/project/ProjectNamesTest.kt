package de.fgchamburg.vodhelper.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectNamesTest {
    @Test fun `a plain name is kept`() = assertEquals("Hamburg Weekly #12", ProjectNames.folderName("Hamburg Weekly #12"))
    @Test fun `surrounding spaces are trimmed`() = assertEquals("Weekly", ProjectNames.folderName("  Weekly  "))
    @Test fun `forbidden characters become dashes`() {
        assertEquals("SF6- Top 8 - Finals-", ProjectNames.folderName("SF6: Top 8 / Finals?"))
        assertEquals("a-b-c-d-e-f-g", ProjectNames.folderName("a<b>c\"d|e*f\\g"))
    }
    @Test fun `trailing dots are dropped`() = assertEquals("Weekly", ProjectNames.folderName("Weekly..."))
    @Test fun `control characters become dashes`() = assertEquals("tab-here", ProjectNames.folderName("tab\there"))
    @Test fun `names with nothing usable give null`() {
        assertNull(ProjectNames.folderName(""))
        assertNull(ProjectNames.folderName("   "))
        assertNull(ProjectNames.folderName("..."))
    }
    @Test fun `Windows reserved names get a trailing dash`() {
        assertEquals("con-", ProjectNames.folderName("con"))
        assertEquals("COM1.txt-", ProjectNames.folderName("COM1.txt"))
        assertEquals("Console", ProjectNames.folderName("Console"))
    }
}
