import de.fgchamburg.vodhelper.buildlogic.PrepareVideoTools
import de.fgchamburg.vodhelper.buildlogic.ToolPlatform
import de.fgchamburg.vodhelper.buildlogic.VideoToolPins
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

// Kotlin Multiplatform with one target: the desktop JVM (macOS and Windows).
kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":core"))
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}

// Tools for the machine building the app (CI builds each OS on its own runner). null on Linux: nothing bundled.
val toolPlatform: ToolPlatform? = ToolPlatform.host(System.getProperty("os.name"), System.getProperty("os.arch"))
val videoToolsDir = layout.buildDirectory.dir("video-tools")

val prepareVideoTools by tasks.registering(PrepareVideoTools::class) {
    description = "Downloads the pinned ffmpeg, yt-dlp and VLC engine for this OS and unpacks them for bundling."
    val supported = toolPlatform != null // plain local, so the onlyIf lambda doesn't capture the script
    onlyIf("tools are only bundled on macOS and Windows") { supported }
    platform.set(toolPlatform ?: ToolPlatform.MACOS_ARM64) // never runs when toolPlatform is null (onlyIf)
    pins.set(toolPlatform?.let { p -> VideoToolPins.all(p).map { "${it.url} ${it.sha256}" } } ?: emptyList())
    notices.set(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"))
    cacheDir.set(File(gradle.gradleUserHomeDir, "caches/fgc-vod-helper/video-tools"))
    outputDir.set(videoToolsDir)
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareVideoTools) }

// jpackage drops the executable bit when it copies app resources into the macOS app, so the bundled
// programs couldn't start. Put it back on the app folder that the .dmg is made from.
val macExecutables = listOf("ffmpeg/ffmpeg", "ffmpeg/ffprobe", "yt-dlp/yt-dlp_macos")
val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app")
tasks.matching { it.name == "createDistributable" }.configureEach {
    val isMac = toolPlatform?.isWindows == false
    val imageDir = appImageDir
    val executables = macExecutables // locals only, so the action doesn't capture the script
    doLast {
        if (!isMac) return@doLast
        val resources = imageDir.get().asFile.listFiles { f -> f.name.endsWith(".app") }.orEmpty()
            .map { File(it, "Contents/app/resources") }
            .singleOrNull() ?: throw GradleException("Expected one .app in ${imageDir.get().asFile}")
        executables.map { File(resources, it) }.forEach { tool ->
            if (!tool.isFile || !tool.setExecutable(true, false)) throw GradleException("Couldn't make $tool executable")
        }
    }
}

// Unit tests stay offline. `make ui-snapshots` passes -Pui.snapshots.dir and gets the real tools to render.
val snapshotsDir: String? = providers.gradleProperty("ui.snapshots.dir").orNull
val videoToolsProps: Map<String, String> = toolPlatform?.let { p ->
    mapOf(
        "videotools.dir" to videoToolsDir.get().dir(p.resourceDir).asFile.absolutePath,
        "videotools.version.ffmpeg" to VideoToolPins.FFMPEG_VERSION,
        "videotools.version.yt-dlp" to VideoToolPins.YT_DLP_VERSION,
        "videotools.version.vlc" to VideoToolPins.VLC_VERSION,
    )
} ?: emptyMap()

val jvmTestTask = tasks.named<Test>("jvmTest") {
    maxHeapSize = "1g" // the Compose UI tests together outgrow the 512 MB default
    filter { excludeTestsMatching("*BundledVideoToolsTest") }
    if (snapshotsDir != null) {
        dependsOn(prepareVideoTools)
        systemProperty("ui.snapshots.dir", snapshotsDir)
        videoToolsProps["videotools.dir"]?.let { systemProperty("videotools.dir", it) }
    }
}

val bundledToolsTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the bundled video tools from the build output (downloads them once)."
    val jvmTest = jvmTestTask.get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    useJUnit()
    filter { includeTestsMatching("*BundledVideoToolsTest") }
    dependsOn(prepareVideoTools)
    videoToolsProps.forEach { (k, v) -> systemProperty(k, v) }
}

tasks.named("check") { dependsOn(bundledToolsTest) }

compose.desktop {
    application {
        mainClass = "de.fgchamburg.vodhelper.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "FGC VOD Helper"
            packageVersion = project.version.toString()
            appResourcesRootDir.set(videoToolsDir)
            description = "Turns Twitch tournament VODs into YouTube-ready videos"
            vendor = "FGC Hamburg"

            // From :app:suggestRuntimeModules; without these the packaged app can crash on a missing JDK module.
            modules("java.instrument", "jdk.security.auth", "jdk.unsupported")

            macOS {
                bundleID = "de.fgchamburg.vodhelper"
            }
            windows {
                // Fixed forever: Windows uses it to recognise upgrades of this app. Never change it.
                upgradeUuid = "6f3c2b1e-8d4a-4c7e-9b1f-2a5d7e9c0b34"
                perUserInstall = true
                menuGroup = "FGC Hamburg"
                shortcut = true
                dirChooser = false
            }
        }
    }
}
