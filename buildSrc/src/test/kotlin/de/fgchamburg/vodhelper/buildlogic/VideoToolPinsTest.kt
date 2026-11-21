package de.fgchamburg.vodhelper.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoToolPinsTest {
    @Test
    fun `vlc falls back to the VideoLAN mirror redirector`() {
        assertEquals(
            listOf(
                "https://download.videolan.org/pub/videolan/vlc/3.0.24/win64/vlc-3.0.24-win64.zip",
                "https://get.videolan.org/vlc/3.0.24/win64/vlc-3.0.24-win64.zip",
            ),
            VideoToolPins.vlc(ToolPlatform.WINDOWS_X64).urls,
        )
    }

    @Test
    fun `other tools have a single source`() {
        assertEquals(1, VideoToolPins.ffmpeg(ToolPlatform.MACOS_ARM64).urls.size)
        assertEquals(1, VideoToolPins.ytDlp(ToolPlatform.MACOS_ARM64).urls.size)
    }
}
