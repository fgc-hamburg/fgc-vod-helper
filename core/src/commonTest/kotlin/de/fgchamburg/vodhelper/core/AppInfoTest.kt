package de.fgchamburg.vodhelper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppInfoTest {
    @Test
    fun `version label shows the version`() {
        assertEquals("Version 1.2.3", AppInfo.from(version = "1.2.3").versionLabel)
    }

    @Test
    fun `name is the app name`() {
        assertEquals("FGC VOD Helper", AppInfo.from(version = "1.2.3").name)
    }

    @Test
    fun `twitch is configured only with a non-blank client id`() {
        assertFalse(AppInfo.from(version = "1.0.0", twitchClientId = "").twitchConfigured)
        assertFalse(AppInfo.from(version = "1.0.0", twitchClientId = "  ").twitchConfigured)
        assertTrue(AppInfo.from(version = "1.0.0", twitchClientId = "abc").twitchConfigured)
    }

    @Test
    fun `youtube is configured only when both google values are set`() {
        assertFalse(AppInfo.from(version = "1.0.0", googleClientId = "id", googleClientSecret = "").youtubeConfigured)
        assertFalse(AppInfo.from(version = "1.0.0", googleClientId = "", googleClientSecret = "secret").youtubeConfigured)
        assertTrue(AppInfo.from(version = "1.0.0", googleClientId = "id", googleClientSecret = "secret").youtubeConfigured)
    }

    @Test
    fun `from keeps the twitch client id`() {
        assertEquals("abc", AppInfo.from(version = "1.0.0", twitchClientId = "abc").twitchClientId)
    }
}
