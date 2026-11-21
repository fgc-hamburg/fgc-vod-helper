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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchAccountTest {
    private val now = 1_000L
    private val clock = Clock { now }
    private val store = InMemorySecretStore()
    private val stored = StoredAccount(AccountKind.TWITCH, store, true)

    private val tokenOk = HttpResponse(
        200,
        """{"access_token":"a2","expires_in":14000,"refresh_token":"r2","token_type":"bearer"}""",
    )
    private val validateOk = HttpResponse(200, """{"client_id":"cid"}""")

    private fun account(handler: (HttpRequest) -> HttpResponse): Pair<TwitchAccount, FakeHttpClient> {
        val http = FakeHttpClient(handler)
        return TwitchAccount(stored, TwitchAuth("cid", http, clock)) to http
    }

    private fun signedIn(expiresIn: Long?) = stored.save(
        AccountCredential(
            "FGCHamburg", "a1", refreshSecret = "r1", accountId = "123",
            expiresAtEpochSeconds = expiresIn?.let { now + it },
        ),
    )

    private fun tokenRequests(http: FakeHttpClient) = http.requests.count { it.url == TwitchAuth.TOKEN_URL }

    @Test
    fun `check status refreshes an expiring token and saves the new refresh token`() = runTest {
        signedIn(60)
        val (account, _) = account { tokenOk }
        assertEquals(AccountStatus.Connected("FGCHamburg"), account.checkStatus())
        val saved = assertNotNull(stored.credential())
        assertEquals("a2", saved.secret)
        assertEquals("r2", saved.refreshSecret)
        assertEquals(now + 14000, saved.expiresAtEpochSeconds)
        assertEquals("123", saved.accountId)
    }

    @Test
    fun `check status validates a fresh token`() = runTest {
        signedIn(3600)
        val (account, http) = account { validateOk }
        assertEquals(AccountStatus.Connected("FGCHamburg"), account.checkStatus())
        assertEquals(listOf(TwitchAuth.VALIDATE_URL), http.requests.map { it.url })
    }

    @Test
    fun `invalid token is refreshed`() = runTest {
        signedIn(3600)
        val (account, http) = account { if (it.url == TwitchAuth.VALIDATE_URL) HttpResponse(401, "{}") else tokenOk }
        assertEquals(AccountStatus.Connected("FGCHamburg"), account.checkStatus())
        assertEquals(1, tokenRequests(http))
        assertEquals("a2", stored.credential()?.secret)
    }

    @Test
    fun `rejected refresh marks the account as needing reconnect`() = runTest {
        signedIn(60)
        val (account, _) = account { HttpResponse(400, """{"status":400,"message":"Invalid refresh token"}""") }
        assertEquals(AccountStatus.NeedsReconnect("FGCHamburg"), account.checkStatus())
        val saved = assertNotNull(stored.credential())
        assertTrue(saved.needsReconnect)
        assertEquals("a1", saved.secret)
    }

    @Test
    fun `offline keeps the account connected`() = runTest {
        signedIn(3600)
        val before = stored.credential()
        val (account, _) = account { throw NetworkException("down") }
        assertEquals(AccountStatus.Connected("FGCHamburg"), account.checkStatus())
        assertEquals(before, stored.credential())
    }

    @Test
    fun `not connected makes no requests`() = runTest {
        val (account, http) = account { tokenOk }
        assertEquals(AccountStatus.NotConnected, account.checkStatus())
        assertEquals(TokenResult.NotConnected, account.accessToken())
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `access token refreshes when close to expiry`() = runTest {
        signedIn(60)
        val (account, http) = account { tokenOk }
        assertEquals(TokenResult.Token("a2"), account.accessToken())
        assertEquals(1, tokenRequests(http))
        assertEquals("r2", stored.credential()?.refreshSecret)
    }

    @Test
    fun `access token uses a fresh token without requests`() = runTest {
        signedIn(3600)
        val (account, http) = account { tokenOk }
        assertEquals(TokenResult.Token("a1"), account.accessToken())
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `access token reports lost access`() = runTest {
        signedIn(60)
        val (account, http) = account { HttpResponse(401, "{}") }
        assertEquals(TokenResult.AccessLost, account.accessToken())
        assertEquals(true, stored.credential()?.needsReconnect)
        assertEquals(TokenResult.AccessLost, account.accessToken())
        assertEquals(1, tokenRequests(http))
    }

    @Test
    fun `access token is offline when refresh cannot reach twitch`() = runTest {
        signedIn(60)
        val (account, _) = account { throw NetworkException("down") }
        assertEquals(TokenResult.Offline, account.accessToken())
        assertNull(stored.credential()?.takeIf { it.needsReconnect })
    }

    @Test
    fun `force refresh rotates the token`() = runTest {
        signedIn(3600)
        val (account, http) = account { tokenOk }
        assertEquals(TokenResult.Token("a2"), account.forceRefresh())
        assertEquals(1, tokenRequests(http))
        assertEquals("r2", stored.credential()?.refreshSecret)
    }

    @Test
    fun `parallel access token calls refresh once`() = runTest {
        signedIn(60)
        val (account, http) = account { tokenOk }
        val results = listOf(async { account.accessToken() }, async { account.accessToken() }).awaitAll()
        assertEquals(listOf(TokenResult.Token("a2"), TokenResult.Token("a2")), results)
        assertEquals(1, tokenRequests(http))
    }
}
