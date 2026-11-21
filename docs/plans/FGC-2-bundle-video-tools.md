# FGC-2: Ship the video tools inside the app — Plan

> For agentic workers: built task by task via `implement-ticket` (one subagent per task, in order). Steps use checkboxes.

**Ticket:** FGC-2 — Ship the video tools inside the app — https://linear.app/xunfosechunfos/issue/FGC-2/ship-the-video-tools-inside-the-app

**Goal:** The Mac and Windows installers carry pinned ffmpeg, ffprobe, yt-dlp and VLC's engine. The app finds them in its own folder, knows their exact versions, shows them, and reports clearly when one is missing or broken.

**Spec:** `docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md` ("Approaches considered → Video player", "Determinism", "Parts → Packaging").

**Tech stack:** as FGC-1 (Gradle 9.3.0, Kotlin 2.4.21 KMP `jvm()`, Compose Multiplatform 1.12.2, JDK 21) plus `net.java.dev.jna:jna:5.19.1` in `core` and a new `buildSrc` (`kotlin-dsl`).

## Acceptance checks

| # | Scope bullet | Verified by |
|---|---|---|
| 1 | Mac and Windows installers include ffmpeg, ffprobe, yt-dlp and VLC's engine | `PrepareVideoTools` puts all four in the app resources; CI `package` job runs `make verify-package` (the packaged app's `--check-tools`) on macOS and Windows |
| 2 | App finds and runs the tools without anything installed | `BundledVideoToolsTest` runs every bundled tool from the build output on both CI OSes; `make verify-package` runs them from the packaged app; VLC is loaded only from the bundled folder (never a system install) |
| 3 | App knows each tool's exact version and shows it in settings | `BundledVideoToolsTest` asserts versions equal the pins; `ToolVersionParserTest`; `AppScreenTest.shows video tool versions`; manual `make run` screenshot. Settings screen doesn't exist yet (FGC-3), so the versions show in a "Video tools" section on the main screen, which FGC-3 moves into settings |
| 4 | Missing or broken tool → app says which one and stops that step clearly | `VideoToolsCheckerTest` (missing file, start failure, error exit, timeout, bad output, VLC load failure); `VideoToolsReportTest.require throws with the tool name`; `AppScreenTest.shows a missing tool`; `--check-tools` exits 1 naming the tool |

## Approach

- **Pins live in one file**, `buildSrc/src/main/kotlin/de/fgchamburg/vodhelper/buildlogic/VideoToolPins.kt`: version, URL and SHA-256 per tool per platform. Downloads happen only at build time, never at runtime.
  - **ffmpeg + ffprobe 8.1.2:** bytedeco `org.bytedeco:ffmpeg:8.1.2-1.5.14` platform jars from Maven Central (LGPL v3 build: `--enable-version3`, no `--enable-gpl`). The jar contains the `ffmpeg`/`ffprobe` programs plus their shared libraries; on macOS they load libs via `@loader_path`, so copying them into one folder is self-contained. The `libjni*`/`jni*` files are JavaCPP bindings and are skipped.
  - **yt-dlp 2026.10.26:** GitHub release assets `yt-dlp_macos` (universal) and `yt-dlp.exe`. Unlicense.
  - **VLC engine 3.0.24:** from `download.videolan.org`: `vlc-3.0.24-arm64.dmg`, `vlc-3.0.24-intel64.dmg`, `vlc-3.0.24-win64.zip`. Only `libvlc`, `libvlccore` and `plugins/` are copied. LGPL 2.1+ (some plugins GPL 2+).
  - All SHA-256 values were computed on download and match the publishers' own checksums (VideoLAN `.sha256`, yt-dlp `SHA2-256SUMS`, Maven `.sha1`).
- **Per-machine platform.** The build bundles tools for the machine it runs on: `macos-arm64` (CI `macos-latest`), `macos-x64` (Intel Macs, e.g. local dev), `windows-x64`. Other hosts (Linux) bundle nothing; the build still works and the real-tools test is skipped there.
- **Gradle task `:app:prepareVideoTools`** (`PrepareVideoTools`, in `buildSrc`): downloads into `<gradle user home>/caches/fgc-vod-helper/video-tools/` (survives `make clean`), verifies SHA-256 (build fails naming the file and both hashes on mismatch, and deletes the bad file), unpacks into `app/build/video-tools/<platform>/`, and copies `THIRD_PARTY_NOTICES.md` into `app/build/video-tools/common/`. Up-to-date checks skip it when pins and notices are unchanged. The macOS dmg is opened with `hdiutil attach -readonly -nobrowse` and always detached.
- **Shipped with Compose `appResourcesRootDir`** = `app/build/video-tools`. At runtime the folder is `System.getProperty("compose.application.resources.dir")`, set by Compose in the packaged app and in `make run`.
- **Bundled layout** (inside the resources folder):
  - macOS: `ffmpeg/ffmpeg`, `ffmpeg/ffprobe`, `ffmpeg/lib*.dylib`, `yt-dlp/yt-dlp`, `vlc/lib/libvlc.dylib`, `vlc/lib/libvlccore.dylib`, `vlc/plugins/**`
  - Windows: `ffmpeg/ffmpeg.exe`, `ffmpeg/ffprobe.exe`, `ffmpeg/*.dll`, `yt-dlp/yt-dlp.exe`, `vlc/libvlc.dll`, `vlc/libvlccore.dll`, `vlc/plugins/**`, `vlc/COPYING.txt`
  - Both: `THIRD_PARTY_NOTICES.md`
- **VLC is checked with JNA, not vlcj.** vlcj's discovery also searches system VLC installs (we must use only ours), and vlcj is GPL v3, which would bind the whole app's license. That choice belongs to FGC-12 (the player), so it's flagged there. This ticket loads `libvlccore` then `libvlc` by full path with JNA, sets `VLC_PLUGIN_PATH`, reads `libvlc_get_version()` and creates and releases one `libvlc_new` instance. That fails if the plugins don't load, which proves the engine works.
- **Runtime check** (`core`): `VideoToolsChecker` runs `ffmpeg -version`, `ffprobe -version`, `yt-dlp --version` (30 s timeout each) through a `ProcessRunner` interface, plus the VLC probe through a `VlcEngineProbe` interface. Both have fakes in tests. Result: `VideoToolsReport` with `Ready(version)`, `Missing(path)` or `Broken(detail)` per tool, in a fixed order (ffmpeg, ffprobe, yt-dlp, VLC engine).
- **"Stops that step clearly":** later steps (download, export, player) call `report.require(tool)`, which throws `VideoToolUnavailableException` with a plain message such as "Can't continue: ffmpeg is missing. Reinstall the app to fix this." No such step exists yet, so this ticket provides and tests the guard. The screen lists every problem.
- **Main screen** gains a "Video tools" section listing each tool with its version or problem ("Checking video tools…" while the check runs in the background).
- **`--check-tools`:** `main(args)` with `--check-tools` prints one line per tool and exits 0 when all are ready, 1 otherwise, without opening a window. `scripts/verify-package.sh` (`make verify-package`) runs it on the packaged app image. CI `package` job and the release workflow call it after `make package`.
- **Licenses:** `THIRD_PARTY_NOTICES.md` at the repo root (shipped in the app) lists each tool, version, license and source link.

## Steps

### Task 1: Pin, download and bundle the tools (build only)

Files: `buildSrc/build.gradle.kts`, `buildSrc/settings.gradle.kts`, `buildSrc/src/main/kotlin/de/fgchamburg/vodhelper/buildlogic/ToolPlatform.kt`, `.../build/VideoToolPins.kt`, `.../build/PrepareVideoTools.kt`, `buildSrc/src/test/kotlin/de/fgchamburg/vodhelper/buildlogic/ToolPlatformTest.kt`, `.../build/Sha256Test.kt`, `app/build.gradle.kts`, `THIRD_PARTY_NOTICES.md`.

`buildSrc/build.gradle.kts`:
```kotlin
plugins { `kotlin-dsl` }
repositories { mavenCentral() }
dependencies { testImplementation(kotlin("test")) }
tasks.test { useJUnitPlatform() }
```
(`settings.gradle.kts` in buildSrc: `rootProject.name = "buildSrc"`. If `kotlin("test")` needs the JUnit 5 variant, use `testImplementation(kotlin("test-junit5"))`.)

`ToolPlatform.kt`:
```kotlin
package de.fgchamburg.vodhelper.buildlogic

/** The OS + CPU a set of bundled tools is for. [resourceDir] is the Compose appResources subfolder name. */
enum class ToolPlatform(val resourceDir: String, val bytedecoClassifier: String, val isWindows: Boolean) {
    MACOS_ARM64("macos-arm64", "macosx-arm64", false),
    MACOS_X64("macos-x64", "macosx-x86_64", false),
    WINDOWS_X64("windows-x64", "windows-x86_64", true);

    companion object {
        /** null when we don't ship tools for this host (e.g. Linux). */
        fun host(osName: String, osArch: String): ToolPlatform? {
            val arch = when (osArch.lowercase()) { "aarch64", "arm64" -> "arm64"; "x86_64", "amd64" -> "x64"; else -> return null }
            val os = osName.lowercase()
            return when {
                os.startsWith("mac") && arch == "arm64" -> MACOS_ARM64
                os.startsWith("mac") && arch == "x64" -> MACOS_X64
                os.startsWith("windows") && arch == "x64" -> WINDOWS_X64
                else -> null
            }
        }
    }
}
```

`VideoToolPins.kt` (exact values):
```kotlin
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
        PinnedDownload("https://github.com/yt-dlp/yt-dlp/releases/download/$YT_DLP_VERSION/yt-dlp.exe", "f5389d3698ecde4ef06f4e6b1ce0294fe9ac1396228c8990b8d3416d6d177a7d")
    } else {
        PinnedDownload("https://github.com/yt-dlp/yt-dlp/releases/download/$YT_DLP_VERSION/yt-dlp_macos", "0f922c15ccc0b83eefa1c8bc9104e434318c4bf16e408e1ee93b7745a6efe0aa")
    }

    fun vlc(p: ToolPlatform) = when (p) {
        ToolPlatform.MACOS_ARM64 -> PinnedDownload("https://download.videolan.org/pub/videolan/vlc/$VLC_VERSION/macosx/vlc-$VLC_VERSION-arm64.dmg", "64a89d93cdd30b0e97131743e246373db82d6826ea882d265d46d74b136da2b7")
        ToolPlatform.MACOS_X64 -> PinnedDownload("https://download.videolan.org/pub/videolan/vlc/$VLC_VERSION/macosx/vlc-$VLC_VERSION-intel64.dmg", "1ef6c903e2dc026d4e58ddf7b2a9ed0eb6f8caf80a9a9f46f96490340b962707")
        ToolPlatform.WINDOWS_X64 -> PinnedDownload("https://download.videolan.org/pub/videolan/vlc/$VLC_VERSION/win64/vlc-$VLC_VERSION-win64.zip", "fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323")
    }

    fun all(p: ToolPlatform): List<PinnedDownload> = listOf(ffmpeg(p), ytDlp(p), vlc(p))
}
```

`PrepareVideoTools.kt`: an abstract `DefaultTask`, configuration-cache compatible (no `project` access at execution).
- Inputs: `@get:Input abstract val platform: Property<ToolPlatform>`; `@get:Input abstract val pins: ListProperty<String>` (each `"url sha256"`, set from `VideoToolPins.all(platform)` so a pin change re-runs the task); `@get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val notices: RegularFileProperty`.
- `@get:Internal abstract val cacheDir: DirectoryProperty`; `@get:OutputDirectory abstract val outputDir: DirectoryProperty`.
- `@get:Inject abstract val exec: ExecOperations`.
- A top-level `internal fun sha256Hex(file: java.io.File): String` (lowercase hex) and `internal fun verifySha256(file: File, expected: String)` that throws `GradleException("Checksum mismatch for <name>: expected <expected>, got <actual>. The file was deleted; run the build again, and if it keeps failing the pin in VideoToolPins.kt is wrong.")` after deleting the file.
- `@TaskAction`:
  1. Delete and recreate `outputDir`. Create `outputDir/common` and `outputDir/<platform.resourceDir>` (call it `dest`).
  2. For each pin: if `cacheDir/<fileName>` is missing or its hash differs, download from the URL (`java.net.URI(url).toURL().openStream()` into `<fileName>.part`, follow redirects, then move into place), then `verifySha256`. Log `"Downloading <url>"` at lifecycle level.
  3. **ffmpeg** (zip/jar via `java.util.zip.ZipFile`): for every entry directly under `org/bytedeco/ffmpeg/<bytedecoClassifier>/` (no deeper folders), take the file name `n`. macOS: keep `n == "ffmpeg" || n == "ffprobe" || (n.startsWith("lib") && n.endsWith(".dylib") && !n.startsWith("libjni"))`. Windows: keep `n == "ffmpeg.exe" || n == "ffprobe.exe" || (n.endsWith(".dll") && !n.startsWith("jni"))`. Write to `dest/ffmpeg/<n>`. macOS: `setExecutable(true, false)` on `ffmpeg` and `ffprobe`.
  4. **yt-dlp:** copy to `dest/yt-dlp/yt-dlp.exe` (Windows) or `dest/yt-dlp/yt-dlp` (macOS, set executable).
  5. **VLC, Windows** (zip): entries `vlc-<VLC_VERSION>/libvlc.dll`, `vlc-<VLC_VERSION>/libvlccore.dll`, `vlc-<VLC_VERSION>/COPYING.txt` → `dest/vlc/<name>`; every file entry under `vlc-<VLC_VERSION>/plugins/` → `dest/vlc/plugins/<rest of path>`.
  6. **VLC, macOS** (dmg): mount point `temporaryDir/vlc-mount`; `exec.exec { commandLine("hdiutil", "attach", "-nobrowse", "-readonly", "-noautoopen", "-mountpoint", mount, dmg) }`; in `try/finally` always `hdiutil detach <mount> -force` (ignore detach exit value). Copy `VLC.app/Contents/MacOS/lib/libvlc.dylib` and `libvlccore.dylib` (following symlinks: real file contents) to `dest/vlc/lib/`, and the whole tree `VLC.app/Contents/MacOS/plugins/` to `dest/vlc/plugins/` (`Files.walk`, regular files only, keep relative paths).
  7. Copy `notices` to `outputDir/common/THIRD_PARTY_NOTICES.md`.
  8. Log a summary line: `"Video tools for <resourceDir>: ffmpeg <FFMPEG_VERSION>, yt-dlp <YT_DLP_VERSION>, VLC <VLC_VERSION>"`.

`app/build.gradle.kts` additions:
```kotlin
import de.fgchamburg.vodhelper.buildlogic.PrepareVideoTools
import de.fgchamburg.vodhelper.buildlogic.ToolPlatform
import de.fgchamburg.vodhelper.buildlogic.VideoToolPins

// Tools for the machine building the app (CI builds each OS on its own runner). null on Linux: nothing bundled.
val toolPlatform: ToolPlatform? = ToolPlatform.host(System.getProperty("os.name"), System.getProperty("os.arch"))
val videoToolsDir = layout.buildDirectory.dir("video-tools")

val prepareVideoTools by tasks.registering(PrepareVideoTools::class) {
    description = "Downloads the pinned ffmpeg, yt-dlp and VLC engine for this OS and unpacks them for bundling."
    onlyIf("tools are only bundled on macOS and Windows") { toolPlatform != null }
    toolPlatform?.let { p ->
        platform.set(p)
        pins.set(VideoToolPins.all(p).map { "${it.url} ${it.sha256}" })
    }
    notices.set(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"))
    cacheDir.set(File(gradle.gradleUserHomeDir, "caches/fgc-vod-helper/video-tools"))
    outputDir.set(videoToolsDir)
}
```
- In `nativeDistributions { … }`: `appResourcesRootDir.set(videoToolsDir)`.
- `tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareVideoTools) }` (Compose's task that copies app resources; used by `run` and packaging).
- `tasks.named<Test>("jvmTest")`: `dependsOn(prepareVideoTools)`; if `toolPlatform != null`, `systemProperty("videotools.dir", videoToolsDir.get().dir(toolPlatform.resourceDir).asFile.absolutePath)` and `systemProperty("videotools.version.ffmpeg", VideoToolPins.FFMPEG_VERSION)`, `systemProperty("videotools.version.yt-dlp", VideoToolPins.YT_DLP_VERSION)`, `systemProperty("videotools.version.vlc", VideoToolPins.VLC_VERSION)`.
- If `platform` must be set even when skipped, give the Property a convention (e.g. `MACOS_ARM64`) only when `toolPlatform == null`; `onlyIf` keeps it from running. Avoid failing configuration on Linux.

`THIRD_PARTY_NOTICES.md` (plain language, no AI art, accurate):
- Intro: the app bundles these programs unchanged; each keeps its own license; source links below.
- **FFmpeg 8.1.2** (ffmpeg, ffprobe and their libraries): LGPL v3 or later. Built by the Bytedeco project (`org.bytedeco:ffmpeg:8.1.2-1.5.14`, non-GPL variant). FFmpeg source: https://ffmpeg.org/releases/ffmpeg-8.1.2.tar.xz. Build script: https://github.com/bytedeco/javacpp-presets/tree/1.5.14/ffmpeg. Note that the build includes third-party codec libraries (e.g. OpenH264, libvpx, LAME, Opus, Speex, libaom, SVT-AV1, libwebp, OpenSSL, FreeType, HarfBuzz, libxml2, SRT, zimg, zlib, opencore-amr, vo-amrwbenc) under their own permissive or LGPL-compatible licenses, listed in the build script above.
- **VLC media player engine 3.0.24** (libVLC, libVLCcore and plugins): libVLC and libVLCcore are LGPL v2.1 or later; some plugins are GPL v2 or later. Source: https://download.videolan.org/pub/videolan/vlc/3.0.24/vlc-3.0.24.tar.xz. License texts: https://www.videolan.org/legal.html.
- **yt-dlp 2026.10.26:** The Unlicense (public domain). The standalone program also contains Python and other components under their own licenses: https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/THIRD_PARTY_LICENSES.txt. Source: https://github.com/yt-dlp/yt-dlp/tree/2026.10.26.
- **JNA 5.19.1** (used to start VLC's engine): Apache 2.0 or LGPL v2.1 (dual). https://github.com/java-native-access/jna.

Steps:
- [ ] Failing tests in buildSrc: `ToolPlatformTest` — `host("Mac OS X","aarch64") == MACOS_ARM64`, `host("Mac OS X","x86_64") == MACOS_X64`, `host("Windows 11","amd64") == WINDOWS_X64`, `host("Linux","amd64") == null`, `host("Windows 11","aarch64") == null`. `Sha256Test` — `sha256Hex` of a temp file containing `abc` == `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`; `verifySha256` with a wrong hash throws `GradleException` whose message contains the file name, and the file no longer exists.
- [ ] Implement `ToolPlatform`, `VideoToolPins`, `PrepareVideoTools`, `THIRD_PARTY_NOTICES.md`, `app/build.gradle.kts` wiring.
- [ ] `./gradlew :buildSrc:test` isn't a thing — buildSrc tests run as part of any build; run `./gradlew -p buildSrc test` to see them.
- [ ] `./gradlew :app:prepareVideoTools` on this Mac produces the macOS layout above under `app/build/video-tools/macos-x64/` (or `macos-arm64`), plus `common/THIRD_PARTY_NOTICES.md`. A second run is UP-TO-DATE. Changing one character of a pinned sha256 makes the build fail with the checksum message (revert after).
- [ ] `make check` passes. Commit (`FGC-2: download and bundle pinned video tools`).

### Task 2: Tool status model, version parsing and messages (`core` commonMain)

Files: `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/tools/VideoTool.kt`, `.../tools/ToolStatus.kt`, `.../tools/VideoToolsReport.kt`, `.../tools/ToolVersionParser.kt`, `.../tools/ToolMessages.kt`; tests in `core/src/commonTest/kotlin/de/fgchamburg/vodhelper/core/tools/`.

Interfaces (package `de.fgchamburg.vodhelper.core.tools`):
```kotlin
enum class VideoTool(val label: String) { FFMPEG("ffmpeg"), FFPROBE("ffprobe"), YT_DLP("yt-dlp"), VLC("VLC engine") }

sealed interface ToolStatus {
    data class Ready(val version: String) : ToolStatus
    data class Missing(val path: String) : ToolStatus
    data class Broken(val detail: String) : ToolStatus
}

/** Status of every tool, always in [VideoTool] order. */
class VideoToolsReport(statuses: Map<VideoTool, ToolStatus>) {
    init { require(statuses.keys == VideoTool.entries.toSet()) { "Report must cover every tool" } }
    val statuses: Map<VideoTool, ToolStatus> = VideoTool.entries.associateWith { statuses.getValue(it) }
    val allReady: Boolean
    /** Version of a ready tool; throws [VideoToolUnavailableException] otherwise. Steps call this before using a tool. */
    fun require(tool: VideoTool): String
    companion object { fun allMissing(path: String): VideoToolsReport }
}

class VideoToolUnavailableException(val tool: VideoTool, val status: ToolStatus) :
    IllegalStateException(ToolMessages.stepBlocked(tool, status))

object ToolVersionParser {
    fun ffmpeg(output: String): String?   // first line "ffmpeg version 8.1.2 Copyright ..." -> "8.1.2"
    fun ffprobe(output: String): String?  // first line "ffprobe version 8.1.2 Copyright ..." -> "8.1.2"
    fun ytDlp(output: String): String?    // first non-blank line, trimmed, must match ^\d{4}\.\d{2}\.\d{2}(\.\d+)?$
    fun vlc(raw: String): String?         // "3.0.24 Vetinari" -> "3.0.24"; must match ^\d+\.\d+\.\d+(\.\d+)?$
}

object ToolMessages {
    const val FIX = "Reinstall the app to fix this."
    fun describe(status: ToolStatus): String
    fun stepBlocked(tool: VideoTool, status: ToolStatus): String
    fun summaryLines(report: VideoToolsReport): List<String>
}
```
Parser rules: ffmpeg/ffprobe use the regex `^(ffmpeg|ffprobe) version (\S+)` on the first non-blank line, and the program name must match the parser. The captured version must start with a digit, else null.

Exact message texts:
- `describe(Ready("8.1.2"))` → `"8.1.2"`
- `describe(Missing(p))` → `"Missing. Reinstall the app to fix this."`
- `describe(Broken("stopped with error code 1"))` → `"Doesn't work (stopped with error code 1). Reinstall the app to fix this."`
- `stepBlocked(FFMPEG, Missing(p))` → `"Can't continue: ffmpeg is missing. Reinstall the app to fix this."`
- `stepBlocked(VLC, Broken("x"))` → `"Can't continue: VLC engine doesn't work (x). Reinstall the app to fix this."`
- `stepBlocked(tool, Ready(..))` → throws `IllegalArgumentException` (never shown for ready tools).
- `summaryLines` → one line per tool in order: `"<label>: <describe(status)>"`, e.g. `"ffmpeg: 8.1.2"`.

Steps:
- [ ] Failing tests: `ToolVersionParserTest` (`ffmpeg reads version from first line`, `ffmpeg rejects ffprobe output`, `ffprobe reads version`, `yt-dlp reads date version`, `yt-dlp rejects other text`, `vlc drops the codename`, `vlc rejects garbage`, `blank output gives null` for each). `ToolMessagesTest` (every exact text above; `summaryLines lists tools in order`). `VideoToolsReportTest` (`statuses follow tool order regardless of input order`, `allReady only when every tool is ready`, `require returns the version`, `require throws with the tool name` — message equals the `stepBlocked` text and `exception.tool == FFMPEG`, `allMissing marks every tool missing`, `report must cover every tool`).
- [ ] Implement. `make check` passes. Commit (`FGC-2: video tool status model and messages`).

### Task 3: Find and check the bundled tools (`core` jvmMain)

Files: `gradle/libs.versions.toml` (add `jna = "5.19.1"` and library `jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }`), `core/build.gradle.kts` (`jvmMain.dependencies { implementation(libs.jna) }`; add `jvmTest.dependencies { implementation(kotlin("test")) }` only if not already inherited), `core/src/jvmMain/kotlin/de/fgchamburg/vodhelper/core/tools/HostOs.kt`, `.../tools/VideoToolsLayout.kt`, `.../tools/ProcessRunner.kt`, `.../tools/SystemProcessRunner.kt`, `.../tools/VlcEngineProbe.kt`, `.../tools/JnaVlcEngineProbe.kt`, `.../tools/VideoToolsChecker.kt`; tests in `core/src/jvmTest/kotlin/de/fgchamburg/vodhelper/core/tools/`.

Interfaces:
```kotlin
enum class HostOs { MAC, WINDOWS;
    companion object { fun from(osName: String): HostOs? /* "Mac OS X"->MAC, "Windows 11"->WINDOWS, else null */; fun current(): HostOs? = from(System.getProperty("os.name")) } }

/** Where each bundled tool sits inside the app's resources folder. */
data class VideoToolsLayout(val root: java.nio.file.Path, val os: HostOs) {
    fun executable(tool: VideoTool): Path   // FFMPEG root/ffmpeg/ffmpeg(.exe); FFPROBE root/ffmpeg/ffprobe(.exe); YT_DLP root/yt-dlp/yt-dlp(.exe); VLC -> IllegalArgumentException
    val vlcLibraryDir: Path                 // MAC root/vlc/lib; WINDOWS root/vlc
    val vlcLibraries: List<Path>            // [libvlccore, libvlc] in that order (load order): .dylib on MAC, .dll on WINDOWS, inside vlcLibraryDir
    val vlcPluginDir: Path                  // root/vlc/plugins
    companion object {
        const val RESOURCES_DIR_PROPERTY = "compose.application.resources.dir"
        /** From the folder Compose gives the running app; null if unset or the OS isn't supported. */
        fun fromSystem(): VideoToolsLayout?
    }
}

data class ProcessResult(val exitCode: Int?, val output: String) // exitCode null = timed out and was stopped; output = stdout+stderr merged
fun interface ProcessRunner { /** Throws IOException if the program can't start. */ fun run(command: List<String>, timeout: kotlin.time.Duration): ProcessResult }
class SystemProcessRunner : ProcessRunner  // ProcessBuilder(command).redirectErrorStream(true); read output on a separate thread so a chatty process can't block; waitFor(timeout); on timeout destroyForcibly() and return exitCode null

fun interface VlcEngineProbe { /** Loads the engine and returns libvlc's raw version string; throws (any Throwable) if it can't. */ fun rawVersion(libraries: List<Path>, pluginDir: Path): String }
class JnaVlcEngineProbe(private val os: HostOs) : VlcEngineProbe

class VideoToolsChecker(
    private val layout: VideoToolsLayout?,
    private val runner: ProcessRunner = SystemProcessRunner(),
    private val vlcProbe: VlcEngineProbe? = layout?.let { JnaVlcEngineProbe(it.os) },
    private val timeout: Duration = 30.seconds,
) { fun check(): VideoToolsReport }
```

Checker rules (each tool independent; one failure never stops the others):
- `layout == null` → `VideoToolsReport.allMissing("video tools folder not found")`.
- ffmpeg/ffprobe/yt-dlp: file doesn't exist → `Missing(path.toString())`. Command `[path, "-version"]` (ffmpeg, ffprobe) or `[path, "--version"]` (yt-dlp). `IOException` → `Broken("couldn't start: ${e.message}")`. `exitCode == null` → `Broken("didn't answer within ${timeout.inWholeSeconds} s")`. `exitCode != 0` → `Broken("stopped with error code $exitCode")`. Parser returns null → `Broken("unexpected version output")`. Else `Ready(version)`.
- VLC: first of `vlcLibraries + vlcPluginDir` that doesn't exist → `Missing(thatPath.toString())`. `vlcProbe.rawVersion(...)` throws (catch `Throwable`, includes `UnsatisfiedLinkError`) → `Broken("couldn't load: ${t.message ?: t::class.simpleName}")`. `ToolVersionParser.vlc` null → `Broken("unexpected version output")`. Else `Ready`.

`JnaVlcEngineProbe.rawVersion`:
1. Set `VLC_PLUGIN_PATH` to `pluginDir` in the native environment: MAC via a JNA `interface CLib : Library { fun setenv(name: String, value: String, overwrite: Int): Int }` loaded with `Native.load("c", CLib::class.java)`; WINDOWS via `interface MsvcRt : Library { fun _putenv(assignment: String): Int }` loaded with `Native.load("msvcrt", ...)`.
2. `NativeLibrary.getInstance(libraries[0].toAbsolutePath().toString())` (libvlccore first, so libvlc's dependency resolves).
3. `val vlc = Native.load(libraries[1].toAbsolutePath().toString(), LibVlc::class.java)` with `interface LibVlc : Library { fun libvlc_get_version(): String; fun libvlc_new(argc: Int, argv: Array<String>?): Pointer?; fun libvlc_release(instance: Pointer) }`.
4. `val version = vlc.libvlc_get_version()`; `val instance = vlc.libvlc_new(0, null) ?: throw IllegalStateException("plugins didn't load")`; `vlc.libvlc_release(instance)`; return `version`.

Steps:
- [ ] Failing tests (all with fakes, no real tools): `HostOsTest` (`mac`, `windows`, `linux is unsupported`). `VideoToolsLayoutTest` (`mac paths`, `windows paths add exe`, `vlc libraries load core first`, `fromSystem is null without the property` — save/restore the system property). `VideoToolsCheckerTest` using a temp dir layout with empty placeholder files, a fake `ProcessRunner` (map from executable file name to `ProcessResult` or an exception) and a fake `VlcEngineProbe`: `all tools ready`, `missing executable is reported as missing with its path`, `start failure is broken`, `error exit is broken with the code`, `timeout is broken`, `unexpected output is broken`, `missing vlc plugins folder is missing`, `vlc load failure is broken and others still ready`, `no layout means everything missing`, `yt-dlp is asked with --version and ffmpeg with -version` (fake records commands). `SystemProcessRunnerTest` (real process, JVM-only, no network): run `[java executable from System.getProperty("java.home")/bin/java(.exe), "-version"]` → exit 0 and output contains "version"; a non-existent program throws `IOException`.
- [ ] Implement. JNA probe has no unit test (needs the real engine) — it's covered by Task 4's `BundledVideoToolsTest`.
- [ ] `make check` passes. Commit (`FGC-2: find and check the bundled video tools`).

### Task 4: Show the tools in the app + `--check-tools` + real-tools test (`app`)

Files: `app/src/jvmMain/kotlin/de/fgchamburg/vodhelper/app/Main.kt`, `.../app/AppScreen.kt`, `.../app/VideoToolsSection.kt`, `.../app/CheckToolsCommand.kt`, `app/src/jvmTest/kotlin/de/fgchamburg/vodhelper/app/AppScreenTest.kt`, `.../app/BundledVideoToolsTest.kt`, `.../app/CheckToolsCommandTest.kt`.

- `@Composable fun AppScreen(info: AppInfo, tools: VideoToolsReport?)`: keeps name + version, then a `VideoToolsSection(tools)` below with some spacing.
- `@Composable fun VideoToolsSection(report: VideoToolsReport?)`: title `"Video tools"` (titleMedium). `report == null` → `"Checking video tools…"`. Otherwise one row per tool in order: label on the left, `ToolMessages.describe(status)` on the right. Non-ready rows use `MaterialTheme.colorScheme.error`. Plain text only, no icons or images.
- `CheckToolsCommand`: `const val CHECK_TOOLS_FLAG = "--check-tools"`; `fun runCheckTools(report: VideoToolsReport, out: (String) -> Unit): Int` prints `ToolMessages.summaryLines(report)` and returns 0 if `allReady` else 1.
- `Main.kt`: `fun main(args: Array<String>)`. If `CHECK_TOOLS_FLAG in args`: `exitProcess(runCheckTools(VideoToolsChecker(VideoToolsLayout.fromSystem()).check(), ::println))` before any Compose/AWT call. Otherwise as before, plus `var tools by remember { mutableStateOf<VideoToolsReport?>(null) }` and `LaunchedEffect(Unit) { tools = withContext(Dispatchers.IO) { VideoToolsChecker(VideoToolsLayout.fromSystem()).check() } }`, then `AppScreen(info, tools)`.
- `BundledVideoToolsTest` (real tools, no network): `val dir = System.getProperty("videotools.dir") ?: return` (skipped where nothing is bundled, e.g. Linux). Build `VideoToolsLayout(Path(dir), HostOs.current()!!)`, run `VideoToolsChecker(layout).check()`, then assert each status is `Ready` with versions `FFMPEG` == property `videotools.version.ffmpeg`, `FFPROBE` == same, `YT_DLP` == `videotools.version.yt-dlp`, `VLC` == `videotools.version.vlc`. On failure, the assertion message includes `ToolMessages.summaryLines(report)`.

Steps:
- [ ] Failing tests: `AppScreenTest` — update existing test to pass `tools = null` and still find name/version; `shows checking while tools are checked` (null → "Checking video tools…"); `shows video tool versions` (all ready → "Video tools", "ffmpeg", "8.1.2", "VLC engine", "3.0.24" exist); `shows a missing tool` (FFMPEG Missing → "Missing. Reinstall the app to fix this." exists). `CheckToolsCommandTest` — `all ready exits 0 and prints each tool`, `a missing tool exits 1 and names it`. `BundledVideoToolsTest.bundled tools run and report the pinned versions`.
- [ ] Implement. Run `make check` (this runs `BundledVideoToolsTest` against the real tools on this Mac). If a real tool's version output doesn't parse, stop and report the actual output — don't loosen the parser without saying so.
- [ ] `make run`: the window shows the four tools with versions. Commit (`FGC-2: show video tools and add --check-tools`).

### Task 5: Check the packaged app in CI + docs

Files: `scripts/verify-package.sh`, `Makefile`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `README.md`, `CLAUDE.md`.

- `scripts/verify-package.sh` (bash, `set -euo pipefail`): finds the packaged app's launcher under `app/build/compose/binaries/main/app/` — macOS: `FGC VOD Helper.app/Contents/MacOS/FGC VOD Helper`; Windows: `FGC VOD Helper/FGC VOD Helper.exe`. Errors clearly if not found ("Run make package first"). Runs it with `--check-tools`, echoes its output, exits with its exit code. Before running, prints a short line saying which launcher it runs.
- `Makefile`: add `verify-package` to `.PHONY` and a target `verify-package: ## Check the packaged app finds and runs its bundled video tools (after make package)` → `@bash scripts/verify-package.sh`.
- `ci.yml` `package` job and `release.yml` `package` job: new step `- name: Check bundled video tools` / `run: make verify-package` right after the package step.
- `README.md`: a short "Bundled video tools" section: which tools and versions, that the build downloads them once (cached in the Gradle user home) and checks their checksums, where the pins live (`buildSrc/.../VideoToolPins.kt`), how to bump one (version + URL + sha256, update `THIRD_PARTY_NOTICES.md`, run `make check` and `make package && make verify-package`), and that installers bundle tools for the building machine's OS and CPU.
- `CLAUDE.md`: under "Code layout" add one bullet: "Bundled video tools (ffmpeg, ffprobe, yt-dlp, VLC engine): pinned in `buildSrc/src/main/kotlin/de/fgchamburg/vodhelper/buildlogic/VideoToolPins.kt`, unpacked by `:app:prepareVideoTools`, licenses in `THIRD_PARTY_NOTICES.md`. Code finds them through `VideoToolsLayout`; steps call `VideoToolsReport.require(tool)` before using one." Add `make verify-package` to the Build and test table.

Steps:
- [ ] Implement script, target, workflow steps, docs.
- [ ] Locally: `make package` then `make verify-package` → prints four ready tools, exit 0. Check the installer size in `dist/` and note it.
- [ ] `make check` passes. Commit (`FGC-2: check bundled tools in the packaged app; docs`).

## Out of scope

- Updating yt-dlp from settings and preferring an app-data copy (FGC-11). `VideoToolsLayout` is the place that will gain that override.
- Recording tool versions in `project.json` at export (FGC-16 / export ticket); `VideoToolsReport.require` returns the version for that.
- The video player and the vlcj decision (FGC-12). **Licensing flag for FGC-12:** vlcj is GPL v3, which would put the whole app under GPL v3. Decide the app's license or use JNA bindings directly.
- Moving the "Video tools" list into the settings screen (FGC-3).
- Shrinking the bundle by dropping unneeded VLC plugins (~93 MB of plugins). Possible follow-up.
- Intel Mac installers: releases build on `macos-latest` (Apple Silicon), so the Mac installer is arm64-only, as it already was for the bundled Java runtime. A `macos-13` (Intel) runner could be added later.
- Code signing / notarization. With unsigned installers, a manual check on a fresh Mac, downloading the release `.dmg` from GitHub, is needed to confirm macOS lets the bundled tools run (quarantine).

## Plan updates

- buildSrc package is `de.fgchamburg.vodhelper.buildlogic`, not `...build`: a folder named `build` is caught by the `build/` ignore rule, which silently left the sources untracked.
- Downloads use connect/read timeouts (30 s / 60 s) so a stalled server fails the build instead of hanging CI.
- **Start-up time (found while building Task 4, measured on an Intel Mac).** ffmpeg/ffprobe answer in ~20 ms. The single-file `yt-dlp_macos` took **17 s on every run**: it unpacks a Python runtime to a temp folder each time, which would also slow every download. The VLC check took ~20 s warm and **~75 s+ on the first launch**: `libvlc_new` loads all 344 plugins because the bundled plugin cache (`plugins.dat`) is stale. VLC checks each plugin's modification time, and both our copy and Compose's resource copy reset them. The Windows zip ships no cache at all, because VLC's own installer builds it. With a valid cache, the engine loads in 0.3 s.
  - **yt-dlp → folder builds:** `yt-dlp_macos.zip` (sha256 `20d98fa608998eff87d1d35a6e9fb65e4b874639a6cc89addc19da3becde3ef1`) and `yt-dlp_win.zip` (sha256 `fd51b212cf8380c7a52ee08c0d6be7f3c59b2f2c832eaf7b45acfae6eab57419`) from the same release. Unzipped whole into `yt-dlp/`, keeping the original launcher names: macOS `yt-dlp/yt-dlp_macos` (set executable), Windows `yt-dlp/yt-dlp.exe`, each next to `_internal/`.
  - **VLC check no longer starts the engine:** `JnaVlcEngineProbe` loads `libvlccore` then `libvlc` by full path and returns `libvlc_get_version()`. No `libvlc_new`, no `VLC_PLUGIN_PATH`. The checker still reports a missing plugins folder. Loading plugins (and making that fast: a valid cache after install, or trimming plugins) belongs to the player, FGC-12, and is listed under Out of scope.

## Out of scope (added while building)

- **VLC plugin loading speed → FGC-12.** Without a valid `plugins.dat`, the first `libvlc_new` after install took ~75 s on an Intel Mac, and ~3 s warm. Options: trim plugins to what playback needs (also shrinks the 93 MB plugin folder), keep plugin modification times through every copy, or build the cache on first run into a writable folder. VLC 3's `--reset-plugins-cache` writes `plugins.dat`.
