import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
}

compose.desktop {
    application {
        mainClass = "de.fgchamburg.vodhelper.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "FGC VOD Helper"
            packageVersion = project.version.toString()
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
