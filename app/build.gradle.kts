import de.fgchamburg.vodhelper.build.PrepareVideoTools
import de.fgchamburg.vodhelper.build.ToolPlatform
import de.fgchamburg.vodhelper.build.VideoToolPins
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

tasks.named<Test>("jvmTest") {
    dependsOn(prepareVideoTools)
    toolPlatform?.let { p ->
        systemProperty("videotools.dir", videoToolsDir.get().dir(p.resourceDir).asFile.absolutePath)
        systemProperty("videotools.version.ffmpeg", VideoToolPins.FFMPEG_VERSION)
        systemProperty("videotools.version.yt-dlp", VideoToolPins.YT_DLP_VERSION)
        systemProperty("videotools.version.vlc", VideoToolPins.VLC_VERSION)
    }
}

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
            modules("java.instrument", "jdk.unsupported")

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
