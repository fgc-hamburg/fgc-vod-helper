package de.fgchamburg.vodhelper.core.twitch

import de.fgchamburg.vodhelper.core.Clock
import de.fgchamburg.vodhelper.core.accounts.AccountCredential
import de.fgchamburg.vodhelper.core.http.FakeHttpClient
import de.fgchamburg.vodhelper.core.http.HttpRequest
import de.fgchamburg.vodhelper.core.http.HttpResponse
import de.fgchamburg.vodhelper.core.http.NetworkException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TwitchAuthTest {
    private var now = 1_000L
    private val clock = Clock { now }

    private val deviceOk = HttpResponse(
        200,
        """{"device_code":"dev","expires_in":1800,"interval":5,"user_code":"ABCD","verification_uri":"https://www.twitch.tv/activate?device-code=ABCD"}""",
    )
    private val tokenOk = HttpResponse(
        200,
        """{"access_token":"acc","expires_in":14000,"refresh_token":"ref","scope":["user:read:broadcast"],"token_type":"bearer"}""",
    )
    private val usersOk = HttpResponse(
        200,
        """{"data":[{"id":"123","login":"fgchamburg","display_name":"FGCHamburg"}]}""",
    )

    private fun error(message: String, status: Int = 400) =
        HttpResponse(status, """{"status":$status,"message":"$message"}""")

    private fun auth(handler: (HttpRequest) -> HttpResponse): Pair<TwitchAuth, FakeHttpClient> {
        val http = FakeHttpClient(handler)
        return TwitchAuth("cid", http, clock) to http
    }

    /** Answers token polls from [answers] in order, users with [usersOk]. */
    private fun polling(vararg answers: HttpResponse, onPoll: () -> Unit = {}): Pair<TwitchAuth, FakeHttpClient> {
        val queue = answers.toMutableList()
        return auth { req ->
            when (req.url) {
                TwitchAuth.TOKEN_URL -> { onPoll(); queue.removeAt(0) }
                TwitchAuth.USERS_URL -> usersOk
                else -> error("unexpected ${req.url}")
            }
        }
    }

    private val code = DeviceCode("ABCD", "https://x", "dev-secret", 5, 1_000 + 1800)

    // start

    @Test
    fun `start sends client id and scopes`() = runTest {
        val (auth, http) = auth { deviceOk }
        auth.startSignIn()
        assertEquals("client_id=cid&scopes=user%3Aread%3Abroadcast", http.requests.single().body)
        assertEquals(TwitchAuth.DEVICE_URL, http.requests.single().url)
    }

    @Test
    fun `start returns the code`() = runTest {
        val (auth, _) = auth { deviceOk }
        val result = assertIs<StartResult.Started>(auth.startSignIn())
        assertEquals(
            DeviceCode("ABCD", "https://www.twitch.tv/activate?device-code=ABCD", "dev", 5, 1_000 + 1800),
            result.code,
        )
    }

    @Test
    fun `start reports offline`() = runTest {
        val (auth, _) = auth { throw NetworkException("down") }
        assertEquals(StartResult.Offline, auth.startSignIn())
    }

    @Test
    fun `start reports a twitch error message`() = runTest {
        val (auth, _) = auth { error("invalid client") }
        assertEquals(StartResult.Failed("invalid client"), auth.startSignIn())
    }

    @Test
    fun `device code toString hides the device code`() {
        assertTrue("dev-secret" !in code.toString())
    }

    // approval

    @Test
    fun `approval after pending polls`() = runTest {
        val (auth, http) = polling(error("authorization_pending"), error("authorization_pending"), tokenOk)
        assertIs<ApprovalResult.Approved>(auth.awaitApproval(code))
        val polls = http.requests.filter { it.url == TwitchAuth.TOKEN_URL }
        assertEquals(3, polls.size)
        assertEquals(
            "client_id=cid&scopes=user%3Aread%3Abroadcast&device_code=dev-secret&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code",
            polls.first().body,
        )
    }

    @Test
    fun `approved sign-in reads the channel display name`() = runTest {
        val (auth, http) = polling(tokenOk)
        val approved = assertIs<ApprovalResult.Approved>(auth.awaitApproval(code))
        assertEquals(
            AccountCredential("FGCHamburg", "acc", "ref", "123", 1_000 + 14_000),
            approved.credential,
        )
        val users = http.requests.single { it.url == TwitchAuth.USERS_URL }
        assertEquals(listOf("Authorization" to "Bearer acc", "Client-Id" to "cid"), users.headers)
    }

    @Test
    fun `failing to read the channel name fails the sign-in`() = runTest {
        val (auth, _) = auth { req ->
            if (req.url == TwitchAuth.TOKEN_URL) tokenOk else HttpResponse(500, "")
        }
        assertEquals(
            ApprovalResult.Failed("Couldn't read the channel name (HTTP 500)"),
            auth.awaitApproval(code),
        )
    }

    @Test
    fun `slow down waits longer`() = runTest {
        val times = mutableListOf<Long>()
        val (auth, _) = polling(error("slow_down"), tokenOk) { times += testScheduler.currentTime }
        assertIs<ApprovalResult.Approved>(auth.awaitApproval(code))
        // first poll after 5 s, then the interval is 10 s
        assertEquals(listOf(5_000L, 15_000L), times)
    }

    @Test
    fun `expired code`() = runTest {
        val (auth, _) = polling(error("invalid device code"))
        assertEquals(ApprovalResult.CodeExpired, auth.awaitApproval(code))
    }

    @Test
    fun `code past its expiry stops polling`() = runTest {
        now = code.expiresAtEpochSeconds
        val (auth, http) = polling(tokenOk)
        assertEquals(ApprovalResult.CodeExpired, auth.awaitApproval(code))
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `denied`() = runTest {
        val (auth, _) = polling(error("access_denied"))
        assertEquals(ApprovalResult.Denied, auth.awaitApproval(code))
    }

    @Test
    fun `network errors keep polling`() = runTest {
        var first = true
        val (auth, _) = auth { req ->
            when (req.url) {
                TwitchAuth.TOKEN_URL -> if (first) { first = false; throw NetworkException("down") } else tokenOk
                else -> usersOk
            }
        }
        assertIs<ApprovalResult.Approved>(auth.awaitApproval(code))
    }

    @Test
    fun `unknown error fails with its message`() = runTest {
        val (auth, _) = polling(error("something odd"))
        assertEquals(ApprovalResult.Failed("something odd"), auth.awaitApproval(code))
    }

    // refresh

    private val credential = AccountCredential("FGCHamburg", "old", "oldref", "123", 5, needsReconnect = true)

    @Test
    fun `refresh keeps name and id and stores the rotated refresh token`() = runTest {
        val (auth, _) = auth { tokenOk }
        val result = assertIs<RefreshResult.Refreshed>(auth.refresh(credential))
        assertEquals(
            AccountCredential("FGCHamburg", "acc", "ref", "123", 1_000 + 14_000, needsReconnect = false),
            result.credential,
        )
    }

    @Test
    fun `refresh body is form encoded`() = runTest {
        val (auth, http) = auth { tokenOk }
        auth.refresh(credential.copy(refreshSecret = "a/b=c"))
        assertEquals(
            "client_id=cid&grant_type=refresh_token&refresh_token=a%2Fb%3Dc",
            http.requests.single().body,
        )
    }

    @Test
    fun `rejected refresh is access lost`() = runTest {
        val (auth, _) = auth { error("Invalid refresh token") }
        assertEquals(RefreshResult.AccessLost, auth.refresh(credential))
    }

    @Test
    fun `missing refresh token is access lost`() = runTest {
        val (auth, http) = auth { tokenOk }
        assertEquals(RefreshResult.AccessLost, auth.refresh(credential.copy(refreshSecret = null)))
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `refresh server error is offline`() = runTest {
        val (auth, _) = auth { HttpResponse(503, "") }
        assertEquals(RefreshResult.Offline, auth.refresh(credential))
    }

    @Test
    fun `refresh network error is offline`() = runTest {
        val (auth, _) = auth { throw NetworkException("down") }
        assertEquals(RefreshResult.Offline, auth.refresh(credential))
    }

    // validate

    @Test
    fun `validate valid`() = runTest {
        val (auth, http) = auth { HttpResponse(200, "{}") }
        assertEquals(ValidateResult.Valid, auth.validate("tok"))
        assertEquals(listOf("Authorization" to "OAuth tok"), http.requests.single().headers)
    }

    @Test
    fun `validate invalid`() = runTest {
        val (auth, _) = auth { HttpResponse(401, "") }
        assertEquals(ValidateResult.Invalid, auth.validate("tok"))
    }

    @Test
    fun `validate offline`() = runTest {
        assertEquals(ValidateResult.Offline, auth { throw NetworkException("x") }.first.validate("tok"))
        assertEquals(ValidateResult.Offline, auth { HttpResponse(500, "") }.first.validate("tok"))
    }
}
