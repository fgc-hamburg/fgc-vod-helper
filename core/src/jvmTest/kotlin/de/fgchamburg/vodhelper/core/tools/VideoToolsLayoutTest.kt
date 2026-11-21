package de.fgchamburg.vodhelper.core.tools

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VideoToolsLayoutTest {
    private val root: Path = Paths.get("res")

    @Test
    fun `mac paths`() {
        val l = VideoToolsLayout(root, HostOs.MAC)
        assertEquals(root.resolve("ffmpeg/ffmpeg"), l.executable(VideoTool.FFMPEG))
        assertEquals(root.resolve("ffmpeg/ffprobe"), l.executable(VideoTool.FFPROBE))
        assertEquals(root.resolve("yt-dlp/yt-dlp_macos"), l.executable(VideoTool.YT_DLP))
        assertEquals(root.resolve("vlc/lib"), l.vlcLibraryDir)
        assertEquals(root.resolve("vlc/plugins"), l.vlcPluginDir)
        assertFailsWith<IllegalArgumentException> { l.executable(VideoTool.VLC) }
    }

    @Test
    fun `windows paths add exe`() {
        val l = VideoToolsLayout(root, HostOs.WINDOWS)
        assertEquals(root.resolve("ffmpeg/ffmpeg.exe"), l.executable(VideoTool.FFMPEG))
        assertEquals(root.resolve("ffmpeg/ffprobe.exe"), l.executable(VideoTool.FFPROBE))
        assertEquals(root.resolve("yt-dlp/yt-dlp.exe"), l.executable(VideoTool.YT_DLP))
        assertEquals(root.resolve("vlc"), l.vlcLibraryDir)
        assertEquals(root.resolve("vlc/plugins"), l.vlcPluginDir)
    }

    @Test
    fun `vlc libraries load core first`() {
        assertEquals(
            listOf(root.resolve("vlc/lib/libvlccore.dylib"), root.resolve("vlc/lib/libvlc.dylib")),
            VideoToolsLayout(root, HostOs.MAC).vlcLibraries,
        )
        assertEquals(
            listOf(root.resolve("vlc/libvlccore.dll"), root.resolve("vlc/libvlc.dll")),
            VideoToolsLayout(root, HostOs.WINDOWS).vlcLibraries,
        )
    }

    @Test
    fun `fromSystem is null without the property`() {
        val key = VideoToolsLayout.RESOURCES_DIR_PROPERTY
        val saved = System.getProperty(key)
        try {
            System.clearProperty(key)
            assertNull(VideoToolsLayout.fromSystem())
        } finally {
            if (saved != null) System.setProperty(key, saved) else System.clearProperty(key)
        }
    }
}
