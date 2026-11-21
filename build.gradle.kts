plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
}

// The single source of the app version: gradle.properties, or -Papp.version=x.y.z.
val appVersion: String = providers.gradleProperty("app.version").get()

require(Regex("""^[1-9]\d*\.\d+\.\d+$""").matches(appVersion)) {
    "app.version must look like 1.2.3 with the first number 1 or higher (macOS installers require it), but was '$appVersion'."
}

allprojects {
    version = appVersion
}
