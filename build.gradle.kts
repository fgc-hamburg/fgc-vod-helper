plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
}

// The single source of the app version: gradle.properties, or -Papp.version=x.y.z.
val appVersion: String = providers.gradleProperty("app.version").get()

// macOS installers need the first number >= 1; Windows installers need x and y <= 255 and z <= 65535.
val versionParts = Regex("""^(\d{1,5})\.(\d{1,5})\.(\d{1,5})$""").matchEntire(appVersion)?.groupValues?.drop(1)?.map { it.toInt() }
require(
    versionParts != null &&
        versionParts[0] in 1..255 && versionParts[1] in 0..255 && versionParts[2] in 0..65535,
) {
    "app.version must look like 1.2.3: first number 1-255, second 0-255, third 0-65535 (installer limits), but was '$appVersion'."
}

allprojects {
    version = appVersion
}
