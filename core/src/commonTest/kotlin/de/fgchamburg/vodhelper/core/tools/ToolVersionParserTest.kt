package de.fgchamburg.vodhelper.core.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolVersionParserTest {
    @Test
    fun `ffmpeg reads version from first line`() {
        assertEquals("8.1.2", ToolVersionParser.ffmpeg("ffmpeg version 8.1.2 Copyright (c) 2000-2026 the FFmpeg developers\nbuilt with clang"))
        assertEquals("8.1.2", ToolVersionParser.ffmpeg("\n  \nffmpeg version 8.1.2 Copyright"))
    }

    @Test
    fun `ffmpeg rejects ffprobe output`() {
        assertNull(ToolVersionParser.ffmpeg("ffprobe version 8.1.2 Copyright"))
    }

    @Test
    fun `ffmpeg rejects version not starting with a digit`() {
        assertNull(ToolVersionParser.ffmpeg("ffmpeg version N-12345-gabc Copyright"))
    }

    @Test
    fun `ffprobe reads version`() {
        assertEquals("8.1.2", ToolVersionParser.ffprobe("ffprobe version 8.1.2 Copyright (c) 2007-2026"))
        assertNull(ToolVersionParser.ffprobe("ffmpeg version 8.1.2 Copyright"))
    }

    @Test
    fun `yt-dlp reads date version`() {
        assertEquals("2026.10.26", ToolVersionParser.ytDlp("2026.10.26\n"))
        assertEquals("2026.10.26.1", ToolVersionParser.ytDlp("\n  2026.10.26.1  \n"))
    }

    @Test
    fun `yt-dlp rejects other text`() {
        assertNull(ToolVersionParser.ytDlp("yt-dlp 2026.10.26"))
        assertNull(ToolVersionParser.ytDlp("2026.10"))
    }

    @Test
    fun `vlc drops the codename`() {
        assertEquals("3.0.24", ToolVersionParser.vlc("3.0.24 Vetinari"))
        assertEquals("3.0.24", ToolVersionParser.vlc("3.0.24"))
    }

    @Test
    fun `vlc rejects garbage`() {
        assertNull(ToolVersionParser.vlc("Vetinari 3.0.24"))
        assertNull(ToolVersionParser.vlc("3.0 Vetinari"))
    }

    @Test
    fun `blank output gives null`() {
        assertNull(ToolVersionParser.ffmpeg(""))
        assertNull(ToolVersionParser.ffmpeg("  \n "))
        assertNull(ToolVersionParser.ffprobe(""))
        assertNull(ToolVersionParser.ytDlp(" \n"))
        assertNull(ToolVersionParser.vlc("  "))
    }
}
