package de.fgchamburg.vodhelper.core.twitch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val twitchJson = Json { ignoreUnknownKeys = true }

@Serializable
internal class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("expires_in") val expiresIn: Long,
    val interval: Long,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
)

@Serializable
internal class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
internal class TwitchError(val status: Int = 0, val message: String = "")

@Serializable
internal class UsersResponse(val data: List<TwitchUser> = emptyList())

@Serializable
internal class TwitchUser(
    val id: String,
    val login: String = "",
    @SerialName("display_name") val displayName: String,
)
