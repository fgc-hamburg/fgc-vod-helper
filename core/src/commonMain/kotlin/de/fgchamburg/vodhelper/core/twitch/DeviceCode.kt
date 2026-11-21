package de.fgchamburg.vodhelper.core.twitch

/** What the user needs to approve a sign-in on twitch.tv. */
data class DeviceCode(
    val userCode: String,
    val verificationUri: String,
    val deviceCode: String,
    val intervalSeconds: Long,
    val expiresAtEpochSeconds: Long,
) {
    // the device code is a secret until approval
    override fun toString() =
        "DeviceCode(userCode=$userCode, verificationUri=$verificationUri, " +
            "intervalSeconds=$intervalSeconds, expiresAtEpochSeconds=$expiresAtEpochSeconds, deviceCode=***)"
}
