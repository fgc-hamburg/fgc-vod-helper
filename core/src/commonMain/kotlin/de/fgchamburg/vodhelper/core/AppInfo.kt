package de.fgchamburg.vodhelper.core

/** What the app shows about itself: name, version, and which build-time keys are present. */
data class AppInfo(
    val name: String,
    val version: String,
    val twitchConfigured: Boolean,
    val youtubeConfigured: Boolean,
) {
    val versionLabel: String get() = "Version $version"

    companion object {
        const val NAME = "FGC VOD Helper"

        fun from(
            version: String,
            twitchClientId: String = "",
            googleClientId: String = "",
            googleClientSecret: String = "",
        ) = AppInfo(
            name = NAME,
            version = version,
            twitchConfigured = twitchClientId.isNotBlank(),
            youtubeConfigured = googleClientId.isNotBlank() && googleClientSecret.isNotBlank(),
        )

        fun fromBuildConfig() = from(
            version = BuildConfig.VERSION,
            twitchClientId = BuildConfig.TWITCH_CLIENT_ID,
            googleClientId = BuildConfig.GOOGLE_CLIENT_ID,
            googleClientSecret = BuildConfig.GOOGLE_CLIENT_SECRET,
        )
    }
}
