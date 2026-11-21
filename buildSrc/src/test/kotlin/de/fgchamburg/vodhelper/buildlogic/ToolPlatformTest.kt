package de.fgchamburg.vodhelper.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolPlatformTest {
    @Test fun `mac arm`() = assertEquals(ToolPlatform.MACOS_ARM64, ToolPlatform.host("Mac OS X", "aarch64"))
    @Test fun `mac intel`() = assertEquals(ToolPlatform.MACOS_X64, ToolPlatform.host("Mac OS X", "x86_64"))
    @Test fun `windows`() = assertEquals(ToolPlatform.WINDOWS_X64, ToolPlatform.host("Windows 11", "amd64"))
    @Test fun `linux is unsupported`() = assertNull(ToolPlatform.host("Linux", "amd64"))
    @Test fun `windows arm is unsupported`() = assertNull(ToolPlatform.host("Windows 11", "aarch64"))
}
