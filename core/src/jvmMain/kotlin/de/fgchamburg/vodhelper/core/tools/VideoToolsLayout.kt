package de.fgchamburg.vodhelper.core.tools

import java.nio.file.Path
import java.nio.file.Paths

/** Where each bundled tool sits inside the app's resources folder. */
data class VideoToolsLayout(val root: Path, val os: HostOs) {
    private val exe = if (os == HostOs.WINDOWS) ".exe" else ""
    private val lib = if (os == HostOs.WINDOWS) ".dll" else ".dylib"

    fun executable(tool: VideoTool): Path = when (tool) {
        VideoTool.FFMPEG -> root.resolve("ffmpeg").resolve("ffmpeg$exe")
        VideoTool.FFPROBE -> root.resolve("ffmpeg").resolve("ffprobe$exe")
        // The folder build: the launcher sits next to its _internal/ folder.
        VideoTool.YT_DLP -> root.resolve("yt-dlp").resolve(if (os == HostOs.WINDOWS) "yt-dlp.exe" else "yt-dlp_macos")
        VideoTool.VLC -> throw IllegalArgumentException("${tool.label} is a library, not a program")
    }

    val vlcLibraryDir: Path
        get() = if (os == HostOs.MAC) root.resolve("vlc").resolve("lib") else root.resolve("vlc")

    /** libvlccore first, then libvlc: the order they must be loaded in. */
    val vlcLibraries: List<Path>
        get() = listOf(vlcLibraryDir.resolve("libvlccore$lib"), vlcLibraryDir.resolve("libvlc$lib"))

    val vlcPluginDir: Path
        get() = root.resolve("vlc").resolve("plugins")

    companion object {
        const val RESOURCES_DIR_PROPERTY = "compose.application.resources.dir"

        /** From the folder Compose gives the running app; null if unset or the OS isn't supported. */
        fun fromSystem(): VideoToolsLayout? {
            val dir = System.getProperty(RESOURCES_DIR_PROPERTY)?.takeIf { it.isNotBlank() } ?: return null
            val os = HostOs.current() ?: return null
            return VideoToolsLayout(Paths.get(dir), os)
        }
    }
}
