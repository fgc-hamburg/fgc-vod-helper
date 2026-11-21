package de.fgchamburg.vodhelper.core.tools

/** The video tools shipped inside the app. Declaration order is the display order. */
enum class VideoTool(val label: String) {
    FFMPEG("ffmpeg"),
    FFPROBE("ffprobe"),
    YT_DLP("yt-dlp"),
    VLC("VLC engine"),
}
