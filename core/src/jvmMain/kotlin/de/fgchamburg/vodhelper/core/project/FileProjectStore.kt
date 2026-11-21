package de.fgchamburg.vodhelper.core.project

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * Keeps each project in `<folder>/project.json`. Saves write a temp file, flush it to disk and
 * rename it over the old file, so after a crash the file is either the old or the new version.
 */
class FileProjectStore internal constructor(
    private val writeTemp: (Path, ByteArray) -> Unit,
) : ProjectStore {
    constructor() : this(::writeAndSync)

    override fun create(parentFolder: String, name: String): CreateResult {
        val folderName = ProjectNames.folderName(name) ?: return CreateResult.Refused(ProjectMessages.NAME_REQUIRED)
        val parent = Path.of(parentFolder)
        if (!Files.isDirectory(parent)) return CreateResult.Refused(ProjectMessages.folderMissing(parentFolder))
        return try {
            val folder = parent.resolve(folderName).toAbsolutePath()
            if (Files.exists(folder.resolve(ProjectJson.FILE_NAME))) {
                return CreateResult.Refused(ProjectMessages.alreadyExists(folderName))
            }
            Files.createDirectories(folder)
            val project = Project(name = name.trim())
            save(folder.toString(), project)
            CreateResult.Created(folder.toString(), project)
        } catch (e: IOException) {
            CreateResult.Refused(ProjectMessages.couldNotCreate(e.message ?: "unknown error"))
        }
    }

    override fun load(folder: String): LoadResult {
        val dir = Path.of(folder)
        val file = dir.resolve(ProjectJson.FILE_NAME)
        if (!Files.isRegularFile(file)) return LoadResult.Missing
        val text = try {
            Files.readString(file)
        } catch (e: IOException) {
            return LoadResult.Unreadable(e.message ?: "unknown error")
        } catch (e: java.nio.charset.CharacterCodingException) {
            return LoadResult.Unreadable("not valid UTF-8")
        }
        removeLeftoverTempFiles(dir)
        return when (val decoded = ProjectJson.decode(text)) {
            is ProjectDecodeResult.Ok -> LoadResult.Loaded(decoded.project)
            ProjectDecodeResult.TooNew -> LoadResult.TooNew
            is ProjectDecodeResult.Unreadable -> LoadResult.Unreadable(decoded.reason)
        }
    }

    override fun save(folder: String, project: Project) {
        val dir = Path.of(folder)
        val tmp = Files.createTempFile(dir, TEMP_PREFIX, TEMP_SUFFIX)
        try {
            writeTemp(tmp, ProjectJson.encode(project).toByteArray(Charsets.UTF_8))
            Files.move(tmp, dir.resolve(ProjectJson.FILE_NAME), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    /** Temp files from a save that was cut short; best effort, errors ignored. */
    private fun removeLeftoverTempFiles(dir: Path) {
        try {
            Files.newDirectoryStream(dir, "$TEMP_PREFIX*$TEMP_SUFFIX").use { leftovers ->
                for (path in leftovers) runCatching { Files.deleteIfExists(path) }
            }
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val TEMP_PREFIX = ".project-"
        const val TEMP_SUFFIX = ".json.tmp"
    }
}

private fun writeAndSync(path: Path, bytes: ByteArray) {
    FileChannel.open(path, WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
}
