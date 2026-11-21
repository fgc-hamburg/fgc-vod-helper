package de.fgchamburg.vodhelper.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildConfigTest {
    @Test
    fun `version matches the Gradle property`() {
        assertEquals(System.getProperty("app.version"), BuildConfig.VERSION)
    }

    @Test
    fun `app info from build config uses the build version`() {
        assertEquals(BuildConfig.VERSION, AppInfo.fromBuildConfig().version)
    }
}
