package de.fgchamburg.vodhelper.core.twitch

import de.fgchamburg.vodhelper.core.Clock
import de.fgchamburg.vodhelper.core.accounts.AccountCredential
import de.fgchamburg.vodhelper.core.http.HttpClient
import de.fgchamburg.vodhelper.core.http.HttpRequest
import de.fgchamburg.vodhelper.core.http.HttpResponse
import de.fgchamburg.vodhelper.core.http.NetworkException
import kotlinx.coroutines.delay

sealed interface StartResult {
    data class Started(val code: DeviceCode) : StartResult
    data class Failed(val reason: String) : StartResult
    data object Offline : StartResult
}

sealed interface ApprovalResult {
    data class Approved(val credential: AccountCredential) : ApprovalResult
    data object CodeExpired : ApprovalResult
    data object Denied : ApprovalResult
    data class Failed(val reason: String) : ApprovalResult
}

sealed interface RefreshResult {
    data class Refreshed(val credential: AccountCredential) : RefreshResult
    data object AccessLost : RefreshResult
    data object Offline : RefreshResult
}

sealed interface ValidateResult {
    data object Valid : ValidateResult
    data object Invalid : ValidateResult
    data object Offline : ValidateResult
}

/** Twitch sign-in: device code flow, token refresh and validation. All HTTP goes through [http]. */
class TwitchAuth(private val clientId: String, private val http: HttpClient, val clock: Clock) {
    companion object {
        const val SCOPES = "user:read:broadcast"
        const val DEVICE_URL = "https://id.twitch.tv/oauth2/device"
        const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        const val VALIDATE_URL = "https://id.twitch.tv/oauth2/validate"
        const val USERS_URL = "https://api.twitch.tv/helix/users"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        const val SLOW_DOWN_STEP_SECONDS = 5L
    }

    /** Used by later tickets for the `Client-Id` header. */
    val clientIdValue: String get() = clientId

    suspend fun startSignIn(): StartResult {
        val response = try {
            http.send(HttpRequest.form(DEVICE_URL, listOf("client_id" to clientId, "scopes" to SCOPES)))
        } catch (e: NetworkException) {
            return StartResult.Offline
        }
        if (!response.isSuccess) return StartResult.Failed(errorMessage(response))
        val parsed = parse<DeviceCodeResponse>(response.body)
            ?: return StartResult.Failed("Unexpected answer from Twitch")
        return StartResult.Started(
            DeviceCode(
                userCode = parsed.userCode,
                verificationUri = parsed.verificationUri,
                deviceCode = parsed.deviceCode,
                intervalSeconds = parsed.interval,
                expiresAtEpochSeconds = clock.nowEpochSeconds() + parsed.expiresIn,
            ),
        )
    }

    suspend fun awaitApproval(code: DeviceCode): ApprovalResult {
        var interval = code.intervalSeconds
        while (true) {
            delay(interval * 1000)
            if (clock.nowEpochSeconds() >= code.expiresAtEpochSeconds) return ApprovalResult.CodeExpired
            val response = try {
                http.send(
                    HttpRequest.form(
                        TOKEN_URL,
                        listOf(
                            "client_id" to clientId,
                            "scopes" to SCOPES,
                            "device_code" to code.deviceCode,
                            "grant_type" to DEVICE_GRANT,
                        ),
                    ),
                )
            } catch (e: NetworkException) {
                continue // temporary; keep polling until the code expires
            }
            if (response.isSuccess) {
                val token = parse<TokenResponse>(response.body)
                    ?: return ApprovalResult.Failed("Unexpected answer from Twitch")
                return readChannel(token)
            }
            val message = errorMessage(response)
            when (message.lowercase()) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += SLOW_DOWN_STEP_SECONDS
                "invalid device code", "expired_token" -> return ApprovalResult.CodeExpired
                "access_denied" -> return ApprovalResult.Denied
                else -> return ApprovalResult.Failed(message)
            }
        }
    }

    suspend fun refresh(credential: AccountCredential): RefreshResult {
        val refreshToken = credential.refreshSecret ?: return RefreshResult.AccessLost
        val response = try {
            http.send(
                HttpRequest.form(
                    TOKEN_URL,
                    listOf(
                        "client_id" to clientId,
                        "grant_type" to "refresh_token",
                        "refresh_token" to refreshToken,
                    ),
                ),
            )
        } catch (e: NetworkException) {
            return RefreshResult.Offline
        }
        return when {
            response.isSuccess -> {
                val token = parse<TokenResponse>(response.body) ?: return RefreshResult.Offline
                RefreshResult.Refreshed(
                    credential.copy(
                        secret = token.accessToken,
                        refreshSecret = token.refreshToken,
                        expiresAtEpochSeconds = clock.nowEpochSeconds() + token.expiresIn,
                        needsReconnect = false,
                    ),
                )
            }
            response.status == 400 || response.status == 401 -> RefreshResult.AccessLost
            else -> RefreshResult.Offline
        }
    }

    suspend fun validate(accessToken: String): ValidateResult {
        val response = try {
            http.send(HttpRequest.get(VALIDATE_URL, listOf("Authorization" to "OAuth $accessToken")))
        } catch (e: NetworkException) {
            return ValidateResult.Offline
        }
        return when {
            response.isSuccess -> ValidateResult.Valid
            response.status == 401 -> ValidateResult.Invalid
            else -> ValidateResult.Offline
        }
    }

    private suspend fun readChannel(token: TokenResponse): ApprovalResult {
        val failed = { status: String -> ApprovalResult.Failed("Couldn't read the channel name ($status)") }
        val response = try {
            http.send(
                HttpRequest.get(
                    USERS_URL,
                    listOf("Authorization" to "Bearer ${token.accessToken}", "Client-Id" to clientId),
                ),
            )
        } catch (e: NetworkException) {
            return failed("offline")
        }
        if (!response.isSuccess) return failed("HTTP ${response.status}")
        val user = parse<UsersResponse>(response.body)?.data?.firstOrNull() ?: return failed("no channel returned")
        return ApprovalResult.Approved(
            AccountCredential(
                accountName = user.displayName,
                secret = token.accessToken,
                refreshSecret = token.refreshToken,
                accountId = user.id,
                expiresAtEpochSeconds = clock.nowEpochSeconds() + token.expiresIn,
            ),
        )
    }

    private fun errorMessage(response: HttpResponse): String =
        parse<TwitchError>(response.body)?.message?.takeIf { it.isNotBlank() } ?: "HTTP ${response.status}"

    private inline fun <reified T> parse(body: String): T? =
        try {
            twitchJson.decodeFromString<T>(body)
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
