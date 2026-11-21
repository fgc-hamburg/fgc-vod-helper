package de.fgchamburg.vodhelper.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildConfigTest {
    @Test
    fun `version matches the Gradle property`() {
        // Set by Gradle's test task; other runners (e.g. the IDE's) don't pass it.
        val expected = System.getProperty("app.version") ?: return
        assertEquals(expected, BuildConfig.VERSION)
    }

    @Test
    fun `app info from build config uses the build version`() {
        assertEquals(BuildConfig.VERSION, AppInfo.fromBuildConfig().version)
    }
}
