package de.fgchamburg.vodhelper.core.project

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileProjectStoreTest {
    private fun tempDir(): Path = Files.createTempDirectory("project-test")

    private val store = FileProjectStore()

    private val sample = Project(
        name = "Weekly",
        vods = listOf(Vod(id = "123", title = "Day 1", broadcastStart = "2026-11-14T13:00:05Z", lengthMillis = 3_600_000)),
        matches = listOf(Match(start = VodPosition("123", 1_000), end = VodPosition("123", 61_000))),
    )

    private fun names(dir: Path): List<String> = dir.listDirectoryEntries().map { it.name }.sorted()

    @Test
    fun `create makes the project folder and file`() {
        val parent = tempDir()
        val result = store.create(parent.toAbsolutePath().toString(), "Hamburg Weekly #12")
        val folder = parent.resolve("Hamburg Weekly #12").toAbsolutePath()
        val expected = Project(name = "Hamburg Weekly #12")
        assertEquals(CreateResult.Created(folder.toString(), expected), result)
        assertEquals(ProjectJson.encode(expected), folder.resolve("project.json").readText())
    }

    @Test
    fun `create refuses a folder that already has a project`() {
        val parent = tempDir().toAbsolutePath().toString()
        store.create(parent, "Weekly")
        assertEquals(CreateResult.Refused(ProjectMessages.alreadyExists("Weekly")), store.create(parent, "Weekly"))
    }

    @Test
    fun `create refuses a blank name`() {
        val parent = tempDir().toAbsolutePath().toString()
        assertEquals(CreateResult.Refused(ProjectMessages.NAME_REQUIRED), store.create(parent, "   "))
    }

    @Test
    fun `create refuses a missing parent folder`() {
        val parent = tempDir().resolve("gone").toAbsolutePath().toString()
        assertEquals(CreateResult.Refused(ProjectMessages.folderMissing(parent)), store.create(parent, "Weekly"))
    }

    @Test
    fun `create works when the project folder exists but is empty`() {
        val parent = tempDir()
        parent.resolve("Weekly").createDirectories()
        assertIs<CreateResult.Created>(store.create(parent.toAbsolutePath().toString(), "Weekly"))
    }

    @Test
    fun `create refuses a folder that has other files`() {
        val parent = tempDir()
        parent.resolve("Weekly").createDirectories().resolve("clip.mp4").writeText("x")
        assertEquals(
            CreateResult.Refused(ProjectMessages.folderNotEmpty("Weekly")),
            store.create(parent.toAbsolutePath().toString(), "Weekly"),
        )
        assertEquals(listOf("clip.mp4"), names(parent.resolve("Weekly")))
    }

    private fun posix(dir: Path): Boolean = Files.getFileAttributeView(dir, PosixFileAttributeView::class.java) != null

    @Test
    fun `a new project file is readable by others`() {
        val dir = tempDir()
        if (!posix(dir)) return
        store.save(dir.toString(), sample)
        assertEquals("rw-r--r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("project.json"))))
    }

    @Test
    fun `save keeps the file's permissions`() {
        val dir = tempDir()
        if (!posix(dir)) return
        store.save(dir.toString(), sample)
        Files.setPosixFilePermissions(dir.resolve("project.json"), PosixFilePermissions.fromString("rw-rw-r--"))
        store.save(dir.toString(), sample.copy(name = "Renamed"))
        assertEquals("rw-rw-r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("project.json"))))
    }

    @Test
    fun `save then load returns the same project`() {
        val folder = tempDir().toAbsolutePath().toString()
        store.save(folder, sample)
        assertEquals(LoadResult.Loaded(sample), store.load(folder))
    }

    @Test
    fun `save leaves no temp files`() {
        val folder = tempDir()
        store.save(folder.toAbsolutePath().toString(), sample)
        assertEquals(listOf("project.json"), names(folder))
    }

    @Test
    fun `a failed write leaves the old file intact`() {
        val folder = tempDir()
        val path = folder.toAbsolutePath().toString()
        store.save(path, sample)
        val failing = FileProjectStore { tmp, bytes ->
            Files.write(tmp, bytes.copyOf(bytes.size / 2))
            throw IOException("disk full")
        }
        assertFailsWith<IOException> { failing.save(path, sample.copy(name = "Other")) }
        assertEquals(ProjectJson.encode(sample), folder.resolve("project.json").readText())
        assertEquals(listOf("project.json"), names(folder))
    }

    @Test
    fun `leftover temp files are ignored and cleaned up`() {
        val folder = tempDir()
        val path = folder.toAbsolutePath().toString()
        store.save(path, sample)
        val leftover = folder.resolve(".project-123.json.tmp")
        leftover.writeText("garbage")
        assertEquals(LoadResult.Loaded(sample), store.load(path))
        assertFalse(leftover.exists())
    }

    @Test
    fun `missing folder or file loads as missing`() {
        val folder = tempDir()
        assertEquals(LoadResult.Missing, store.load(folder.toAbsolutePath().toString()))
        assertEquals(LoadResult.Missing, store.load(folder.resolve("gone").toAbsolutePath().toString()))
    }

    @Test
    fun `an unreadable file is never overwritten by open`() {
        val folder = tempDir()
        val file = folder.resolve("project.json")
        file.writeText("{not json")
        assertIs<LoadResult.Unreadable>(store.load(folder.toAbsolutePath().toString()))
        assertEquals("{not json", file.readText())
    }

    @Test
    fun `a newer file loads as too new`() {
        val folder = tempDir()
        folder.resolve("project.json").writeText("""{"schemaVersion": 2, "name": "x"}""")
        assertEquals(LoadResult.TooNew, store.load(folder.toAbsolutePath().toString()))
    }

    @Test
    fun `file is utf-8 plain text`() {
        val folder = tempDir().toAbsolutePath().toString()
        val project = Project(name = "Kölner Turnier – 東京")
        store.save(folder, project)
        val text = Path.of(folder, "project.json").readBytes().toString(Charsets.UTF_8)
        assertTrue(text.contains("Kölner Turnier – 東京"))
        assertEquals(LoadResult.Loaded(project), store.load(folder))
    }
}
