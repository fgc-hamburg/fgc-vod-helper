package de.fgchamburg.vodhelper.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject

/** Lowercase hex SHA-256 of [file]. */
internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Throws (and deletes [file]) when its SHA-256 isn't [expected]. */
internal fun verifySha256(file: File, expected: String) {
    val actual = sha256Hex(file)
    if (actual != expected) {
        file.delete()
        throw GradleException(
            "Checksum mismatch for ${file.name}: expected $expected, got $actual. " +
                "The file was deleted; run the build again, and if it keeps failing the pin in VideoToolPins.kt is wrong.",
        )
    }
}

/** Downloads the pinned ffmpeg, yt-dlp and VLC engine for one platform and unpacks them for Compose's app resources. */
abstract class PrepareVideoTools : DefaultTask() {
    @get:Input abstract val platform: Property<ToolPlatform>

    /** Each entry is "url sha256", so changing a pin re-runs the task. */
    @get:Input abstract val pins: ListProperty<String>

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val notices: RegularFileProperty
    @get:Internal abstract val cacheDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun prepare() {
        val p = platform.get()
        val out = outputDir.get().asFile
        out.deleteRecursively()
        File(out, "common").mkdirs()
        val dest = File(out, p.resourceDir).also { it.mkdirs() }

        val cache = cacheDir.get().asFile.also { it.mkdirs() }
        val files = VideoToolPins.all(p).map { pin -> download(pin, cache) }
        val (ffmpegJar, ytDlp, vlc) = files

        unpackFfmpeg(ffmpegJar, p, File(dest, "ffmpeg"))
        copyYtDlp(ytDlp, p, File(dest, "yt-dlp"))
        if (p.isWindows) unpackVlcWindows(vlc, File(dest, "vlc")) else unpackVlcMac(vlc, File(dest, "vlc"))

        notices.get().asFile.copyTo(File(out, "common/THIRD_PARTY_NOTICES.md"), overwrite = true)
        logger.lifecycle(
            "Video tools for ${p.resourceDir}: ffmpeg ${VideoToolPins.FFMPEG_VERSION}, " +
                "yt-dlp ${VideoToolPins.YT_DLP_VERSION}, VLC ${VideoToolPins.VLC_VERSION}",
        )
    }

    private fun download(pin: PinnedDownload, cache: File): File {
        val target = File(cache, pin.fileName)
        if (target.exists() && sha256Hex(target) == pin.sha256) return target
        logger.lifecycle("Downloading ${pin.url}")
        val part = File(cache, pin.fileName + ".part")
        URI(pin.url).toURL().openStream().use { input ->
            part.outputStream().use { input.copyTo(it) }
        }
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        verifySha256(target, pin.sha256)
        return target
    }

    private fun unpackFfmpeg(jar: File, p: ToolPlatform, dir: File) {
        dir.mkdirs()
        val prefix = "org/bytedeco/ffmpeg/${p.bytedecoClassifier}/"
        ZipFile(jar).use { zip ->
            for (entry in zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) }) {
                val n = entry.name.removePrefix(prefix)
                if ('/' in n) continue
                val keep = if (p.isWindows) {
                    n == "ffmpeg.exe" || n == "ffprobe.exe" || (n.endsWith(".dll") && !n.startsWith("jni"))
                } else {
                    n == "ffmpeg" || n == "ffprobe" || (n.startsWith("lib") && n.endsWith(".dylib") && !n.startsWith("libjni"))
                }
                if (!keep) continue
                val file = File(dir, n)
                zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
                if (n == "ffmpeg" || n == "ffprobe") file.setExecutable(true, false)
            }
        }
    }

    private fun copyYtDlp(source: File, p: ToolPlatform, dir: File) {
        dir.mkdirs()
        val file = File(dir, if (p.isWindows) "yt-dlp.exe" else "yt-dlp")
        source.copyTo(file, overwrite = true)
        if (!p.isWindows) file.setExecutable(true, false)
    }

    private fun unpackVlcWindows(zipFile: File, dir: File) {
        dir.mkdirs()
        val root = "vlc-${VideoToolPins.VLC_VERSION}/"
        ZipFile(zipFile).use { zip ->
            for (entry in zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(root) }) {
                val rest = entry.name.removePrefix(root)
                val wanted = rest == "libvlc.dll" || rest == "libvlccore.dll" || rest == "COPYING.txt" || rest.startsWith("plugins/")
                if (!wanted) continue
                val file = File(dir, rest)
                file.parentFile.mkdirs()
                zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    private fun unpackVlcMac(dmg: File, dir: File) {
        val mount = File(temporaryDir, "vlc-mount").also { it.mkdirs() }
        exec.exec { commandLine("hdiutil", "attach", "-nobrowse", "-readonly", "-noautoopen", "-mountpoint", mount.absolutePath, dmg.absolutePath) }
        try {
            val macos = File(mount, "VLC.app/Contents/MacOS")
            val lib = File(dir, "lib").also { it.mkdirs() }
            for (name in listOf("libvlc.dylib", "libvlccore.dylib")) {
                // Files.copy follows symlinks by default, so this copies the real contents.
                Files.copy(File(macos, "lib/$name").toPath(), File(lib, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val plugins = File(macos, "plugins").toPath()
            Files.walk(plugins).use { stream ->
                stream.filter { Files.isRegularFile(it) }.sorted().forEach { src ->
                    val target = File(dir, "plugins/" + plugins.relativize(src).toString().replace(File.separatorChar, '/'))
                    target.parentFile.mkdirs()
                    Files.copy(src, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            exec.exec { commandLine("hdiutil", "detach", mount.absolutePath, "-force"); isIgnoreExitValue = true }
        }
    }
}
