package de.fgchamburg.vodhelper.core.settings

import de.fgchamburg.vodhelper.core.tools.HostOs
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDataDirTest {
    @Test
    fun `mac uses application support`() = assertEquals(
        Paths.get("/Users/ann", "Library", "Application Support", "FGC VOD Helper"),
        AppDataDir.resolve(HostOs.MAC, "/Users/ann", null),
    )

    @Test
    fun `windows uses appdata`() = assertEquals(
        Paths.get("C:\\Users\\ann\\AppData\\Roaming", "FGC VOD Helper"),
        AppDataDir.resolve(HostOs.WINDOWS, "C:\\Users\\ann", "C:\\Users\\ann\\AppData\\Roaming"),
    )

    @Test
    fun `windows falls back when appdata is unset`() = assertEquals(
        Paths.get("C:\\Users\\ann", "AppData", "Roaming", "FGC VOD Helper"),
        AppDataDir.resolve(HostOs.WINDOWS, "C:\\Users\\ann", null),
    )
}
