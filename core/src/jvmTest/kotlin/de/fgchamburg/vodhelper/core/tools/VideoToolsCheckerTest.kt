package de.fgchamburg.vodhelper.core.tools

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class VideoToolsCheckerTest {
    private lateinit var root: Path
    private lateinit var layout: VideoToolsLayout
    private val commands = mutableListOf<List<String>>()
    private val answers = mutableMapOf<String, Any>()
    private val vlcCalls = mutableListOf<List<Path>>()
    private var vlcAnswer: () -> String = { "3.0.24 Vetinari" }

    private val runner = ProcessRunner { command, _ ->
        commands += command
        when (val a = answers[Path.of(command[0]).name] ?: error("no answer for ${command[0]}")) {
            is IOException -> throw a
            else -> a as ProcessResult
        }
    }
    private val probe = VlcEngineProbe { libs ->
        vlcCalls += libs
        vlcAnswer()
    }

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("tools")
        layout = VideoToolsLayout(root, HostOs.MAC)
        listOf(VideoTool.FFMPEG, VideoTool.FFPROBE, VideoTool.YT_DLP).forEach {
            layout.executable(it).also { p -> p.parent.createDirectories() }.createFile()
        }
        layout.vlcLibraryDir.createDirectories()
        layout.vlcLibraries.forEach { it.createFile() }
        layout.vlcPluginDir.createDirectories()
        answers["ffmpeg"] = ProcessResult(0, "ffmpeg version 8.1.2 Copyright\n")
        answers["ffprobe"] = ProcessResult(0, "ffprobe version 8.1.2 Copyright\n")
        answers["yt-dlp_macos"] = ProcessResult(0, "2026.10.26\n")
    }

    @AfterTest
    fun tearDown() = root.deleteRecursively()

    private fun check(timeout: Duration = 30.seconds) =
        VideoToolsChecker(layout, runner, probe, timeout).check()

    @Test
    fun `all tools ready`() {
        val report = check()
        assertEquals(
            mapOf(
                VideoTool.FFMPEG to ToolStatus.Ready("8.1.2"),
                VideoTool.FFPROBE to ToolStatus.Ready("8.1.2"),
                VideoTool.YT_DLP to ToolStatus.Ready("2026.10.26"),
                VideoTool.VLC to ToolStatus.Ready("3.0.24"),
            ),
            report.statuses,
        )
        assertEquals(listOf(layout.vlcLibraries), vlcCalls)
    }

    @Test
    fun `missing executable is reported as missing with its path`() {
        val path = layout.executable(VideoTool.FFPROBE)
        Files.delete(path)
        val report = check()
        assertEquals(ToolStatus.Missing(path.toString()), report.statuses[VideoTool.FFPROBE])
        assertIs<ToolStatus.Ready>(report.statuses[VideoTool.FFMPEG])
    }

    @Test
    fun `start failure is broken`() {
        answers["ffmpeg"] = IOException("denied")
        assertEquals(ToolStatus.Broken("couldn't start: denied"), check().statuses[VideoTool.FFMPEG])
    }

    @Test
    fun `error exit is broken with the code`() {
        answers["yt-dlp_macos"] = ProcessResult(3, "boom")
        assertEquals(ToolStatus.Broken("stopped with error code 3"), check().statuses[VideoTool.YT_DLP])
    }

    @Test
    fun `timeout is broken`() {
        answers["ffmpeg"] = ProcessResult(null, "")
        assertEquals(
            ToolStatus.Broken("didn't answer within 5 s"),
            check(5.seconds).statuses[VideoTool.FFMPEG],
        )
    }

    @Test
    fun `unexpected output is broken`() {
        answers["ffprobe"] = ProcessResult(0, "hello")
        assertEquals(ToolStatus.Broken("unexpected version output"), check().statuses[VideoTool.FFPROBE])
    }

    @Test
    fun `missing vlc plugins folder is missing`() {
        Files.delete(layout.vlcPluginDir)
        assertEquals(ToolStatus.Missing(layout.vlcPluginDir.toString()), check().statuses[VideoTool.VLC])
        assertEquals(emptyList(), vlcCalls)
    }

    @Test
    fun `vlc load failure is broken and others still ready`() {
        vlcAnswer = { throw UnsatisfiedLinkError("no luck") }
        val report = check()
        assertEquals(ToolStatus.Broken("couldn't load: no luck"), report.statuses[VideoTool.VLC])
        assertIs<ToolStatus.Ready>(report.statuses[VideoTool.FFMPEG])
        assertIs<ToolStatus.Ready>(report.statuses[VideoTool.YT_DLP])
    }

    @Test
    fun `vlc unexpected version is broken`() {
        vlcAnswer = { "weird" }
        assertEquals(ToolStatus.Broken("unexpected version output"), check().statuses[VideoTool.VLC])
    }

    @Test
    fun `no layout means everything missing`() {
        val report = VideoToolsChecker(null).check()
        assertEquals(VideoToolsReport.allMissing("video tools folder not found").statuses, report.statuses)
    }

    @Test
    fun `yt-dlp is asked with --version and ffmpeg with -version`() {
        check()
        assertEquals(
            listOf(
                listOf(layout.executable(VideoTool.FFMPEG).toString(), "-version"),
                listOf(layout.executable(VideoTool.FFPROBE).toString(), "-version"),
                listOf(layout.executable(VideoTool.YT_DLP).toString(), "--version"),
            ),
            commands,
        )
    }
}
