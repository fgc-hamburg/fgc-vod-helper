package de.fgchamburg.vodhelper.core.accounts

/** The accounts the app signs in to, in the order they are shown. */
enum class AccountKind(val label: String, val required: Boolean, val secretKey: String) {
    TWITCH("Twitch", true, "twitch"),
    STARTGG("start.gg", true, "startgg"),
    YOUTUBE("YouTube", false, "youtube"),
}
