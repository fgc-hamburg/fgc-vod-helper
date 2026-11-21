package de.fgchamburg.vodhelper.core.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostOsTest {
    @Test
    fun `mac`() = assertEquals(HostOs.MAC, HostOs.from("Mac OS X"))

    @Test
    fun `windows`() = assertEquals(HostOs.WINDOWS, HostOs.from("Windows 11"))

    @Test
    fun `linux is unsupported`() = assertNull(HostOs.from("Linux"))
}
