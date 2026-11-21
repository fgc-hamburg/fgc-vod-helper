package de.fgchamburg.vodhelper.core.twitch

import de.fgchamburg.vodhelper.core.Clock
import de.fgchamburg.vodhelper.core.accounts.AccountCredential
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import de.fgchamburg.vodhelper.core.accounts.InMemorySecretStore
import de.fgchamburg.vodhelper.core.accounts.StoredAccount
import de.fgchamburg.vodhelper.core.http.FakeHttpClient
import de.fgchamburg.vodhelper.core.http.HttpRequest
import de.fgchamburg.vodhelper.core.http.HttpResponse
import de.fgchamburg.vodhelper.core.http.NetworkException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class TwitchSignInTest {
    private val clock = Clock { 1_000L }
    private val store = InMemorySecretStore()
    private val stored = StoredAccount(AccountKind.TWITCH, store, true)
    private val uri = "https://www.twitch.tv/activate?device-code=ABCDEFGH"

    private val deviceOk = HttpResponse(
        200,
        """{"device_code":"dev","expires_in":1800,"interval":5,"user_code":"ABCDEFGH","verification_uri":"$uri"}""",
    )
    private val tokenOk = HttpResponse(
        200,
        """{"access_token":"acc","expires_in":14000,"refresh_token":"ref","token_type":"bearer"}""",
    )
    private val usersOk = HttpResponse(
        200,
        """{"data":[{"id":"123","login":"fgchamburg","display_name":"FGCHamburg"}]}""",
    )

    private fun error(message: String) = HttpResponse(400, """{"status":400,"message":"$message"}""")

    private fun signIn(handler: (HttpRequest) -> HttpResponse): Pair<TwitchSignIn, FakeHttpClient> {
        val http = FakeHttpClient(handler)
        return TwitchSignIn(TwitchAccount(stored, TwitchAuth("cid", http, clock))) to http
    }

    private fun answering(token: HttpResponse) = { req: HttpRequest ->
        when (req.url) {
            TwitchAuth.DEVICE_URL -> deviceOk
            TwitchAuth.TOKEN_URL -> token
            TwitchAuth.USERS_URL -> usersOk
            else -> error("unexpected ${req.url}")
        }
    }

    private suspend fun states(signIn: TwitchSignIn): List<TwitchSignInState> {
        val seen = mutableListOf<TwitchSignInState>()
        signIn.run { seen += it }
        return seen
    }

    @Test
    fun `approval saves the account`() = runTest {
        val (signIn, _) = signIn(answering(tokenOk))
        assertEquals(
            listOf(
                TwitchSignInState.Starting,
                TwitchSignInState.WaitingForApproval("ABCDEFGH", uri),
                TwitchSignInState.Connected("FGCHamburg"),
            ),
            states(signIn),
        )
        assertEquals(AccountStatus.Connected("FGCHamburg"), stored.status())
    }

    @Test
    fun `reconnect replaces a lost sign-in`() = runTest {
        stored.save(AccountCredential("FGCHamburg", "old", refreshSecret = "oldr", needsReconnect = true))
        assertEquals(AccountStatus.NeedsReconnect("FGCHamburg"), stored.status())
        val (signIn, _) = signIn(answering(tokenOk))
        states(signIn)
        assertEquals(AccountStatus.Connected("FGCHamburg"), stored.status())
        val saved = assertNotNull(stored.credential())
        assertFalse(saved.needsReconnect)
        assertEquals("acc", saved.secret)
    }

    @Test
    fun `offline start fails with the offline message`() = runTest {
        val (signIn, _) = signIn { throw NetworkException("down") }
        assertEquals(
            listOf(TwitchSignInState.Starting, TwitchSignInState.Failed(TwitchMessages.OFFLINE)),
            states(signIn),
        )
    }

    @Test
    fun `expired code`() = runTest {
        val (signIn, _) = signIn(answering(error("invalid device code")))
        assertEquals(TwitchSignInState.Failed(TwitchMessages.CODE_EXPIRED), states(signIn).last())
    }

    @Test
    fun `denied`() = runTest {
        val (signIn, _) = signIn(answering(error("access_denied")))
        assertEquals(TwitchSignInState.Failed(TwitchMessages.DENIED), states(signIn).last())
    }

    @Test
    fun `twitch error shows the reason`() = runTest {
        val (signIn, _) = signIn { HttpResponse(400, """{"status":400,"message":"invalid client"}""") }
        assertEquals(
            listOf(
                TwitchSignInState.Starting,
                TwitchSignInState.Failed("Couldn't connect to Twitch: invalid client"),
            ),
            states(signIn),
        )
    }

    @Test
    fun `cancelling stops polling`() = runTest {
        val (signIn, http) = signIn(answering(error("authorization_pending")))
        val seen = mutableListOf<TwitchSignInState>()
        val job = launch { signIn.run { seen += it } }
        runCurrent()
        assertEquals(TwitchSignInState.WaitingForApproval("ABCDEFGH", uri), seen.last())
        job.cancel()
        val before = http.requests.count { it.url == TwitchAuth.TOKEN_URL }
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(before, http.requests.count { it.url == TwitchAuth.TOKEN_URL })
    }
}
