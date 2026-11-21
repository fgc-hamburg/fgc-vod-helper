package de.fgchamburg.vodhelper.buildlogic

data class PinnedDownload(val url: String, val sha256: String) {
    val fileName: String get() = url.substringAfterLast('/')
}

/** The only place tool versions are set. Bumping a tool: change version + URL + sha256 here, update THIRD_PARTY_NOTICES.md. */
object VideoToolPins {
    const val FFMPEG_VERSION = "8.1.2"
    const val BYTEDECO_FFMPEG = "8.1.2-1.5.14"
    const val YT_DLP_VERSION = "2026.10.26"
    const val VLC_VERSION = "3.0.24"

    fun ffmpeg(p: ToolPlatform) = PinnedDownload(
        "https://repo1.maven.org/maven2/org/bytedeco/ffmpeg/$BYTEDECO_FFMPEG/ffmpeg-$BYTEDECO_FFMPEG-${p.bytedecoClassifier}.jar",
        when (p) {
            ToolPlatform.MACOS_ARM64 -> "af57468c0bb7b2e9c8c93547e6d599e2b4faa4117118555be92675850972420c"
            ToolPlatform.MACOS_X64 -> "b5a8f5124fb2d04412bd01eb5ca469d153d1c396fd0c67a33d3f9a1501c94c0c"
            ToolPlatform.WINDOWS_X64 -> "67ebc3e6940add83b1b8a9dfd337a5c67556f2b72cb3225efe91a26874445c8e"
        },
    )

    fun ytDlp(p: ToolPlatform) = if (p.isWindows) {
        PinnedDownload("https://github.com/yt-dlp/yt-dlp/releases/download/$YT_DLP_VERSION/yt-dlp_win.zip", "fd51b212cf8380c7a52ee08c0d6be7f3c59b2f2c832eaf7b45acfae6eab57419")
    } else {
        PinnedDownload("https://github.com/yt-dlp/yt-dlp/releases/download/$YT_DLP_VERSION/yt-dlp_macos.zip", "20d98fa608998eff87d1d35a6e9fb65e4b874639a6cc89addc19da3becde3ef1")
    }

    fun vlc(p: ToolPlatform) = when (p) {
        ToolPlatform.MACOS_ARM64 -> PinnedDownload("https://download.videolan.org/pub/videolan/vlc/$VLC_VERSION/macosx/vlc-$VLC_VERSION-arm64.dmg", "64a89d93cdd30b0e97131743e246373db82d6826ea882d265d46d74b136da2b7")
        ToolPlatform.MACOS_X64 -> PinnedDownload("https://download.videolan.org/pub/videolan/vlc/$VLC_VERSION/macosx/vlc-$VLC_VERSION-intel64.dmg", "1ef6c903e2dc026d4e58ddf7b2a9ed0eb6f8caf80a9a9f46f96490340b962707")
        ToolPlatform.WINDOWS_X64 -> PinnedDownload("https://download.videolan.org/pub/videolan/vlc/$VLC_VERSION/win64/vlc-$VLC_VERSION-win64.zip", "fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323")
    }

    fun all(p: ToolPlatform): List<PinnedDownload> = listOf(ffmpeg(p), ytDlp(p), vlc(p))
}
