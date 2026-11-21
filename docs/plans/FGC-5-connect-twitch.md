# FGC-5: Connect Twitch — Plan

> For agentic workers: built task by task via `implement-ticket` (one subagent per task, in order). Steps use checkboxes.

**Ticket:** FGC-5 — Connect Twitch — https://linear.app/xunfosechunfos/issue/FGC-5/connect-twitch

**Goal:** From setup, the user clicks "Connect" on the Twitch row, sees a short code, approves it in the browser, and setup then shows "Connected as <channel name>". The sign-in survives restarts, renews itself, and when Twitch takes access away the row says so and offers "Reconnect".

**Spec:** `docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md` ("First-time setup" step 1, "Parts → Twitch", "Left to the builder"). Linear doc "Setup: Register the Twitch app".

**Tech stack:** as FGC-4. New in `core`: `kotlinx-coroutines-core` 1.9.0 (commonMain; same version Compose already pulls into the app) and `kotlinx-coroutines-test` 1.9.0 (commonTest). HTTP uses the JDK's built-in `java.net.http.HttpClient` (jvmMain) behind an interface; no new HTTP library.

## Acceptance checks

| # | Scope bullet | Verified by |
|---|---|---|
| 1 | From setup, the user clicks "Connect Twitch", sees a short code, and approves it in the browser | `TwitchAuthTest` (device request, polling: pending / slow down / approved / expired / denied / offline); `TwitchSignInTest` (state sequence); `TwitchConnectDialogTest` (shows code, open-browser button calls opener); `AppRootTest.connect twitch shows the code and opens the browser`; snapshot `12-twitch-code.png`; manual: `make run` with a real `TWITCH_CLIENT_ID` (see Manual checks) |
| 2 | Once approved, setup shows the connected channel name | `TwitchAuthTest.approved sign-in reads the channel display name`; `TwitchSignInTest.approval saves the account`; `AppRootTest.after approving twitch setup shows the channel name`; snapshots `13-twitch-connected.png`, `07-setup-ready.png` |
| 3 | The app stays signed in across restarts and renews access on its own | `StoredAccountTest.new credential fields round trip`, `old stored credential still loads`; `TwitchAccountTest.check status refreshes an expiring token and saves the new refresh token`, `check status validates a fresh token`, `invalid token is refreshed`, `access token refreshes when close to expiry`, `parallel access token calls refresh once`, `offline keeps the account connected`; `TwitchAuthTest.refresh keeps name and id and stores the rotated refresh token` |
| 4 | If access is lost (password change, revoked), the app asks to reconnect instead of failing silently | `TwitchAccountTest.rejected refresh marks the account as needing reconnect`, `access token reports lost access`; `AccountMessagesTest.needs reconnect`; `AccountsSectionTest.needs reconnect shows reconnect`; `AppRootTest.lost twitch access asks to reconnect`; snapshot `14-setup-twitch-reconnect.png` |

## Approach

### Decisions

- **Device Code Grant Flow, public client, no secret.** Client ID comes from `BuildConfig.TWITCH_CLIENT_ID` (already generated). `AppInfo` gains `twitchClientId: String` so `Main` can hand it to Twitch code.
- **Scopes: `user:read:broadcast`.** Twitch's device endpoint requires a `scopes` value. Listing a channel's past broadcasts (Helix `GET /videos`) needs no scope; `user:read:broadcast` is the smallest read-only scope tied to the signed-in channel and asks for nothing sensitive. Sub-only VODs are downloaded by yt-dlp, which does not use this sign-in at all (its own login is a matter for FGC-10). Constant: `TwitchAuth.SCOPES`.
- **Channel name** = Helix `GET https://api.twitch.tv/helix/users` `data[0].display_name` (headers `Authorization: Bearer <token>`, `Client-Id: <id>`). Its `id` is stored too (`accountId`), FGC-8 needs it to list videos.
- **Token lifecycle** (all in `TwitchAccount`):
  - On every app start (`checkStatus()`), and before every API call (`accessToken()`): if the access token expires within `REFRESH_MARGIN_SECONDS = 300`, refresh. On start, a token that isn't expiring is validated (`GET https://id.twitch.tv/oauth2/validate`, header `Authorization: OAuth <token>`); 401 → refresh. Twitch requires validating on start.
  - Refresh tokens rotate: every refresh stores the new `refresh_token` immediately.
  - Refresh answered 400 or 401 → **access lost**: the stored credential is kept but flagged `needsReconnect = true`; status becomes `AccountStatus.NeedsReconnect(name)`.
  - Network failure or 5xx → **offline**: nothing changes, the account stays Connected. Being offline is never "lost access".
  - A `Mutex` makes concurrent refreshes happen once (a rotated refresh token can only be used once).
- **Generic account hook.** `Account` gains `suspend fun checkStatus(): AccountStatus = status()` (default: local read). `AppRoot` calls `checkStatus()` instead of `status()` when it loads statuses. FGC-6/7 override it the same way.
- **New status** `AccountStatus.NeedsReconnect(accountName)`. Text: "Access was lost. Reconnect to keep using <name>." Actions: Reconnect, Sign out. Shown in the error colour when required. It counts as not connected for "can start project".
- **Logic out of the UI.** The sign-in loop (`TwitchSignIn.run`) lives in core and reports `TwitchSignInState`s; the dialog only renders the current state. Closing the dialog cancels the coroutine, which stops polling.
- **Browser.** When the code first appears, the app opens Twitch's `verification_uri` (it already contains the code, so the user only clicks "Authorize"), and the dialog also has an "Open twitch.tv/activate" button. Opening goes through `fun interface BrowserOpener` (app) so tests use a fake.
- **Clock.** `fun interface Clock { fun nowEpochSeconds(): Long }` in core; `SystemClock` in jvmMain. Nothing here affects exports, but clocks are still passed in (CLAUDE.md).
- **Secrets never printed.** `HttpRequest.toString()` hides headers and body; `DeviceCode.toString()` hides the device code; `AccountCredential.toString()` already hides secrets (extend for the new fields: show `accountId`, `expiresAtEpochSeconds`, `needsReconnect`, still `secret=***`).

### Twitch endpoints (exact)

All bodies are `application/x-www-form-urlencoded`, fields in the order listed, built with `HttpRequest.form(...)`.

| Call | Request | Success | Errors |
|---|---|---|---|
| Start | `POST https://id.twitch.tv/oauth2/device` `client_id`, `scopes` | 200 `{"device_code","expires_in","interval","user_code","verification_uri"}` | anything else → `Failed` |
| Poll | `POST https://id.twitch.tv/oauth2/token` `client_id`, `scopes`, `device_code`, `grant_type=urn:ietf:params:oauth:grant-type:device_code` | 200 `{"access_token","expires_in","refresh_token",...}` | 400 `{"status":400,"message":"authorization_pending"}` → keep waiting; `"slow_down"` → interval += 5; `"invalid device code"` or `"expired_token"` → code expired; `"access_denied"` → denied; other → failed with message |
| Refresh | `POST https://id.twitch.tv/oauth2/token` `client_id`, `grant_type=refresh_token`, `refresh_token` | 200 as above (new refresh token) | 400/401 → access lost; 5xx / network → offline |
| Validate | `GET https://id.twitch.tv/oauth2/validate`, header `Authorization: OAuth <access>` | 200 | 401 → invalid; 5xx / network → offline |
| User | `GET https://api.twitch.tv/helix/users`, headers `Authorization: Bearer <access>`, `Client-Id: <id>` | 200 `{"data":[{"id","login","display_name"}]}` | anything else / empty data → failed |

JSON: `Json { ignoreUnknownKeys = true }`. DTOs are `internal @Serializable` classes with `@SerialName` snake_case names, in `TwitchJson.kt`.

### Files

core `commonMain` (`de.fgchamburg.vodhelper.core`):
- `Clock.kt` — `fun interface Clock { fun nowEpochSeconds(): Long }`
- `http/HttpClient.kt` — `interface HttpClient { suspend fun send(request: HttpRequest): HttpResponse }` (throws `NetworkException` when the server can't be reached)
- `http/HttpRequest.kt` — `data class HttpRequest(val method: String, val url: String, val headers: List<Pair<String, String>> = emptyList(), val body: String? = null)`, `toString()` = `"HttpRequest($method $url)"`; `companion object { fun get(url, headers = emptyList()); fun form(url, fields: List<Pair<String, String>>, headers = emptyList()) }` (form → method `POST`, header `Content-Type: application/x-www-form-urlencoded` first, body from `formEncode`); `fun formEncode(fields: List<Pair<String, String>>): String` — percent-encodes UTF-8 bytes of every char except `A-Z a-z 0-9 - . _ ~`, uppercase hex, `key=value` joined by `&`, order kept.
- `http/HttpResponse.kt` — `data class HttpResponse(val status: Int, val body: String)` with `val isSuccess get() = status in 200..299`
- `http/NetworkException.kt` — `class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)`
- `http/FakeHttpClient.kt` — for tests (like `InMemorySecretStore`): `class FakeHttpClient(private val handler: (HttpRequest) -> HttpResponse) : HttpClient { val requests: List<HttpRequest> }` — records each request, then calls handler; handler may throw `NetworkException`.
- `accounts/AccountCredential.kt` — add `val accountId: String? = null`, `val expiresAtEpochSeconds: Long? = null`, `val needsReconnect: Boolean = false` (after existing fields).
- `accounts/AccountStatus.kt` — add `data class NeedsReconnect(val accountName: String) : AccountStatus`
- `accounts/Account.kt` — add `suspend fun checkStatus(): AccountStatus = status()`
- `accounts/StoredAccount.kt` — `status()` returns `NeedsReconnect(name)` when `credential.needsReconnect`; add `fun markNeedsReconnect()` (re-saves the credential with the flag; no-op when none stored).
- `accounts/AccountMessages.kt` — `describe(NeedsReconnect)` = `"Access was lost. Reconnect to keep using ${name}."`; `actions(NeedsReconnect)` = `[RECONNECT, SIGN_OUT]`.
- `accounts/Accounts.kt` — `fun create(info: AppInfo, store: SecretStore, twitchAuth: TwitchAuth? = null)`: TWITCH becomes `TwitchAccount(StoredAccount(TWITCH, store, info.twitchConfigured), twitchAuth)` when `twitchAuth != null`, otherwise `StoredAccount` as today.
- `AppInfo.kt` — add `val twitchClientId: String = ""` (last constructor param); `from()` sets it; `twitchConfigured` unchanged.
- `twitch/TwitchJson.kt` — DTOs: `DeviceCodeResponse(deviceCode, expiresIn: Long, interval: Long, userCode, verificationUri)`, `TokenResponse(accessToken, expiresIn: Long, refreshToken)`, `TwitchError(status: Int = 0, message: String = "")`, `UsersResponse(data: List<TwitchUser>)`, `TwitchUser(id, login, displayName)`.
- `twitch/DeviceCode.kt` — `data class DeviceCode(val userCode: String, val verificationUri: String, val deviceCode: String, val intervalSeconds: Long, val expiresAtEpochSeconds: Long)`; `toString()` hides `deviceCode`.
- `twitch/TwitchAuth.kt` — see Task 3.
- `twitch/TwitchAccount.kt` — see Task 4.
- `twitch/TwitchSignIn.kt` + `twitch/TwitchSignInState.kt` + `twitch/TwitchMessages.kt` — see Task 5.

core `jvmMain`: `SystemClock.kt` (`object SystemClock : Clock`, `Instant.now().epochSecond`), `http/JdkHttpClient.kt`.

app `jvmMain`: `BrowserOpener.kt`, `TwitchConnectDialog.kt`, changes to `ConnectDialog.kt`, `AppRoot.kt`, `Main.kt`, `AccountsSection.kt`.

## Steps

### Task 1: HTTP, clock and coroutines in core

**Files:** `gradle/libs.versions.toml`, `core/build.gradle.kts`, the `Clock.kt`, `http/*` files above, `core/src/jvmMain/.../SystemClock.kt`, `core/src/jvmMain/.../http/JdkHttpClient.kt`; tests `core/src/commonTest/.../http/HttpRequestTest.kt`, `core/src/jvmTest/.../http/JdkHttpClientTest.kt`.

- [ ] Add to `libs.versions.toml`: version `kotlinx-coroutines = "1.9.0"`; libraries `kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }`, `kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }`. In `core/build.gradle.kts`: commonMain `implementation(libs.kotlinx.coroutines.core)`, commonTest `implementation(libs.kotlinx.coroutines.test)`.
- [ ] `HttpRequestTest`: `formEncode keeps order and encodes reserved characters` — `formEncode(listOf("client_id" to "abc", "scopes" to "user:read:broadcast", "x" to "a b&c=d/é"))` == `"client_id=abc&scopes=user%3Aread%3Abroadcast&x=a%20b%26c%3Dd%2F%C3%A9"`; `form sets method, content type and body`; `toString hides headers and body` (a request with header `Authorization: Bearer secret123` and body `refresh_token=secret456` → `toString()` contains neither secret).
- [ ] `JdkHttpClient(timeout: java.time.Duration = Duration.ofSeconds(30)) : HttpClient` — builds a `java.net.http.HttpRequest` (method + body publisher: `ofString(body)` or `noBody()`; adds headers in order), `sendAsync(..., BodyHandlers.ofString()).await()` (from `kotlinx.coroutines.future.await`), returns `HttpResponse(status, body)`. Any `java.io.IOException` (including `HttpTimeoutException`, `ConnectException`) → `NetworkException("Couldn't reach ${uri.host}", e)`. Unwrap `CompletionException` causes.
- [ ] `JdkHttpClientTest` (jvmTest) with `com.sun.net.httpserver.HttpServer` on `127.0.0.1:0`: `sends method, headers and body and returns status and body` (POST form; server echoes; check server saw `Content-Type` and body; response 201 + body); `non-2xx is returned not thrown` (404); `unreachable server throws NetworkException` (bind a server, note the port, stop it, call it).
- [ ] `SystemClock` (no test needed beyond compile).
- [ ] `make check` passes. Commit `FGC-5: http client, clock and coroutines in core`.

### Task 2: Account model for renewable sign-ins

**Files:** `AccountCredential.kt`, `AccountStatus.kt`, `Account.kt`, `StoredAccount.kt`, `AccountMessages.kt`, `AppInfo.kt`, `app/.../AccountsSection.kt` (only so the `when`s compile: `NeedsReconnect` uses the error colour like not-connected); tests `StoredAccountTest`, `AccountMessagesTest`, `AccountsOverviewTest`, `AppInfoTest`, `app/.../AccountsSectionTest`.

- [ ] Tests first:
  - `StoredAccountTest.new credential fields round trip` (save with `accountId="123"`, `expiresAtEpochSeconds=1_000`, `needsReconnect=false` → `credential()` equals it); `old stored credential still loads` (raw JSON `{"accountName":"a","secret":"s"}` in the store → loads with nulls/false); `needs reconnect flag shows needs reconnect` (status == `NeedsReconnect("a")`); `markNeedsReconnect keeps the sign-in and sets the flag`; `markNeedsReconnect without a sign-in does nothing`; `toString hides secrets` (refresh secret too).
  - `AccountMessagesTest.needs reconnect` — describe text exactly `"Access was lost. Reconnect to keep using fgchamburg."`, actions `[RECONNECT, SIGN_OUT]`.
  - `AccountsOverviewTest.needs reconnect blocks starting a project` — Twitch `NeedsReconnect`, start.gg Connected → `missingRequired == [TWITCH]`.
  - `AppInfoTest.from keeps the twitch client id`.
  - `AccountsSectionTest.needs reconnect shows reconnect` — row shows the describe text and "Reconnect" + "Sign out" buttons.
- [ ] Implement as listed in Files. `StoredAccount.status()` order: not configured → none stored → needsReconnect → connected.
- [ ] `make check` passes. Commit `FGC-5: accounts can need reconnecting`.

### Task 3: TwitchAuth (device sign-in, refresh, validate)

**Files:** `core/src/commonMain/.../twitch/TwitchJson.kt`, `DeviceCode.kt`, `TwitchAuth.kt`; test `core/src/commonTest/.../twitch/TwitchAuthTest.kt`.

```kotlin
class TwitchAuth(private val clientId: String, private val http: HttpClient, private val clock: Clock) {
    companion object {
        const val SCOPES = "user:read:broadcast"
        const val DEVICE_URL = "https://id.twitch.tv/oauth2/device"
        const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        const val VALIDATE_URL = "https://id.twitch.tv/oauth2/validate"
        const val USERS_URL = "https://api.twitch.tv/helix/users"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        const val SLOW_DOWN_STEP_SECONDS = 5L
    }
    val clientIdValue: String get() = clientId // used by later tickets for the Client-Id header

    suspend fun startSignIn(): StartResult                     // Started(DeviceCode) | Failed(reason) | Offline
    suspend fun awaitApproval(code: DeviceCode): ApprovalResult // Approved(AccountCredential) | CodeExpired | Denied | Failed(reason)
    suspend fun refresh(credential: AccountCredential): RefreshResult // Refreshed(AccountCredential) | AccessLost | Offline
    suspend fun validate(accessToken: String): ValidateResult  // Valid | Invalid | Offline
}
```

Result types are sealed interfaces nested in `TwitchAuth.kt` (top-level, `sealed interface StartResult { data class Started(val code: DeviceCode); data class Failed(val reason: String); data object Offline }`, etc.).

Behaviour:
- `startSignIn`: POST form to `DEVICE_URL` (`client_id`, `scopes`). 200 → `DeviceCode(userCode, verificationUri, deviceCode, interval, now + expires_in)`. Non-2xx → `Failed(error.message or "HTTP <status>")`. `NetworkException` → `Offline`.
- `awaitApproval`: loop: `delay(interval * 1000)`; if `clock.nowEpochSeconds() >= code.expiresAtEpochSeconds` → `CodeExpired`; POST poll form. 200 → read the user (`USERS_URL`) → `Approved(AccountCredential(accountName = display_name, secret = access, refreshSecret = refresh, accountId = id, expiresAtEpochSeconds = now + expires_in))`; user read fails → `Failed("Couldn't read the channel name (HTTP <status>)")`. 400 messages per the endpoints table (match case-insensitively on `message`). `NetworkException` while polling → keep polling (temporary) until the code expires. Interval starts at `code.intervalSeconds`, `slow_down` adds `SLOW_DOWN_STEP_SECONDS`.
- `refresh`: requires `credential.refreshSecret` (null → `AccessLost`). POST form (`client_id`, `grant_type=refresh_token`, `refresh_token`). 200 → `Refreshed(credential.copy(secret = access, refreshSecret = new refresh, expiresAtEpochSeconds = now + expires_in, needsReconnect = false))`. 400/401 → `AccessLost`. 5xx → `Offline`. `NetworkException` → `Offline`.
- `validate`: GET with `Authorization: OAuth <token>`. 200 → `Valid`, 401 → `Invalid`, other / network → `Offline`.

Tests (`runTest`, so `delay` is virtual; `FakeHttpClient` with a handler matching on URL and a queue of poll answers; fixed clock `Clock { now }` with a `var now = 1_000L` that the handler can advance):
- [ ] `start sends client id and scopes` (body == `"client_id=cid&scopes=user%3Aread%3Abroadcast"`), `start returns the code` (all fields, `expiresAtEpochSeconds == 1_000 + 1800`), `start reports offline`, `start reports a twitch error message` (400 `{"status":400,"message":"invalid client"}` → `Failed("invalid client")`).
- [ ] `approval after pending polls` (pending, pending, 200 → `Approved`, 3 token requests; poll body exactly `client_id=cid&scopes=user%3Aread%3Abroadcast&device_code=dev&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code`), `approved sign-in reads the channel display name` (credential: name `FGCHamburg`, id `123`, secret/refresh from token response, expiry = now + expires_in; users request has `Authorization: Bearer <access>` and `Client-Id: cid`), `slow down waits longer` (assert virtual time via `testScheduler.currentTime`: interval 5 → slow_down → next wait 10 s), `expired code` (message `invalid device code` → `CodeExpired`), `code past its expiry stops polling` (clock past expiry → `CodeExpired` without a token request), `denied` (`access_denied` → `Denied`), `network errors keep polling` (throw, then 200 → `Approved`), `unknown error fails with its message`.
- [ ] `refresh keeps name and id and stores the rotated refresh token`, `refresh body is form encoded` (refresh token with `/` and `=` is percent-encoded), `rejected refresh is access lost` (400 `{"status":400,"message":"Invalid refresh token"}`), `missing refresh token is access lost`, `refresh server error is offline`, `refresh network error is offline`.
- [ ] `validate valid / invalid / offline`.
- [ ] `make check` passes. Commit `FGC-5: twitch device sign-in and token refresh`.

### Task 4: TwitchAccount (stays signed in, renews, detects lost access)

**Files:** `core/src/commonMain/.../twitch/TwitchAccount.kt`, `accounts/Accounts.kt`; tests `core/src/commonTest/.../twitch/TwitchAccountTest.kt`, `AccountsTest` (add cases).

```kotlin
class TwitchAccount(private val stored: StoredAccount, val auth: TwitchAuth) : Account by stored {
    companion object { const val REFRESH_MARGIN_SECONDS = 300L }
    override suspend fun checkStatus(): AccountStatus
    suspend fun accessToken(): TokenResult // Token(value) | NotConnected | AccessLost | Offline
    suspend fun forceRefresh(): TokenResult
}
```

The clock comes from `TwitchAuth`: make its constructor parameter `val clock: Clock` (public). `TokenResult` is a sealed interface in `TwitchAccount.kt`.

Behaviour (all under one `Mutex`):
- `checkStatus()`: `status()` not `Connected` → return it. Credential expiring (`expiresAtEpochSeconds == null || <= now + margin`) → refresh. Otherwise `validate`: `Valid` → Connected; `Invalid` → refresh; `Offline` → Connected. Refresh results: `Refreshed` → `stored.save(new)`, Connected; `AccessLost` → `stored.markNeedsReconnect()`, `NeedsReconnect`; `Offline` → Connected (unchanged).
- `accessToken()`: not connected → `NotConnected`; needs reconnect → `AccessLost`; expiring → refresh (same handling; `AccessLost` marks the flag) → `Token(new.secret)`; else `Token(secret)`. Later tickets call this before every Twitch API call, and on an API 401 call `forceRefresh()`:
- `suspend fun forceRefresh(): TokenResult` — refresh regardless of expiry, same handling.

Tests (`runTest`, `InMemorySecretStore`, `FakeHttpClient`, settable clock):
- [ ] `check status refreshes an expiring token and saves the new refresh token` (stored refresh `r1`, expiry now+60 → after: stored secret `a2`, refresh `r2`, expiry updated; status Connected).
- [ ] `check status validates a fresh token` (expiry now+3600, validate 200 → no token request).
- [ ] `invalid token is refreshed` (validate 401 → refresh 200).
- [ ] `rejected refresh marks the account as needing reconnect` (status `NeedsReconnect(name)`; credential still stored; `needsReconnect` true).
- [ ] `offline keeps the account connected` (validate throws `NetworkException` → Connected, store unchanged).
- [ ] `not connected makes no requests`.
- [ ] `access token refreshes when close to expiry`, `access token reports lost access`, `force refresh rotates the token`.
- [ ] `parallel access token calls refresh once` (two `async { accessToken() }` with expiring token; handler counts token requests == 1; second call returns the new token).
- [ ] `AccountsTest.twitch uses the twitch account when sign-in is available` (`create(info, store, twitchAuth)` → first is `TwitchAccount`), `without sign-in twitch stays a stored account`.
- [ ] `make check` passes. Commit `FGC-5: twitch account renews access and detects lost access`.

### Task 5: Sign-in driver and texts (core)

**Files:** `core/src/commonMain/.../twitch/TwitchSignInState.kt`, `TwitchSignIn.kt`, `TwitchMessages.kt`; tests `TwitchSignInTest.kt`.

```kotlin
sealed interface TwitchSignInState {
    data object Starting : TwitchSignInState
    data class WaitingForApproval(val userCode: String, val verificationUri: String) : TwitchSignInState
    data class Connected(val accountName: String) : TwitchSignInState
    data class Failed(val message: String) : TwitchSignInState   // message is ready to show
}

class TwitchSignIn(private val account: TwitchAccount) {
    /** Runs one sign-in attempt, reporting each state. Cancel the coroutine to stop waiting. */
    suspend fun run(onState: (TwitchSignInState) -> Unit)
}

object TwitchMessages {
    const val STARTING = "Asking Twitch for a code…"
    const val INSTRUCTIONS = "In your browser, sign in with the channel's Twitch account and enter this code:"
    const val OPEN_BROWSER = "Open twitch.tv/activate"
    const val WAITING = "Waiting for you to approve in the browser…"
    const val TRY_AGAIN = "Get a new code"
    const val CANCEL = "Cancel"
    const val DONE = "Done"
    const val OFFLINE = "Couldn't reach Twitch. Check your internet connection and try again."
    const val CODE_EXPIRED = "The code expired before it was approved. Get a new code to try again."
    const val DENIED = "The sign-in was declined on Twitch."
    fun connected(name: String) = "Connected as $name"
    fun failed(reason: String) = "Couldn't connect to Twitch: $reason"
}
```

`run`: emit `Starting` → `auth.startSignIn()` (`Offline` → `Failed(OFFLINE)`; `Failed(r)` → `Failed(failed(r))`) → emit `WaitingForApproval(userCode, verificationUri)` → `auth.awaitApproval(code)`: `Approved(c)` → `account.save(c)` → emit `Connected(c.accountName)`; `CodeExpired` → `Failed(CODE_EXPIRED)`; `Denied` → `Failed(DENIED)`; `Failed(r)` → `Failed(failed(r))`. A save that throws → `Failed(failed("couldn't save the sign-in: ${e.message}"))`. Never put tokens in messages.

Tests (`runTest`):
- [ ] `approval saves the account` — states `[Starting, WaitingForApproval("ABCDEFGH", uri), Connected("FGCHamburg")]`; afterwards `account.status() == Connected("FGCHamburg")`.
- [ ] `reconnect replaces a lost sign-in` — stored credential with `needsReconnect = true` → after run, status Connected, flag false.
- [ ] `offline start fails with the offline message`, `expired code`, `denied`, `twitch error shows the reason`.
- [ ] `cancelling stops polling` — launch `run`, advance until `WaitingForApproval`, cancel the job, advance 60 s → no further token requests.
- [ ] `make check` passes. Commit `FGC-5: twitch sign-in steps`.

### Task 6: Connect dialog and app wiring

**Files:** `app/src/jvmMain/.../BrowserOpener.kt`, `TwitchConnectDialog.kt`, `ConnectDialog.kt`, `AppRoot.kt`, `Main.kt`; tests `TwitchConnectDialogTest.kt`, `AppRootTest.kt`, `UiSnapshots.kt`.

- [ ] `BrowserOpener.kt`: `fun interface BrowserOpener { fun open(url: String) }` and `object SystemBrowserOpener : BrowserOpener` (uses `java.awt.Desktop` when `isDesktopSupported` and `Action.BROWSE` is supported; failures are ignored because the dialog also shows the address).
- [ ] `TwitchConnectDialog(state: TwitchSignInState, onOpenBrowser: (String) -> Unit, onTryAgain: () -> Unit, onClose: () -> Unit)` — stateless `AlertDialog`, title `AccountMessages.connectTitle(TWITCH)` ("Connect Twitch"):
  - `Starting`: text `STARTING`; button `CANCEL`.
  - `WaitingForApproval`: `INSTRUCTIONS`, the code in `MaterialTheme.typography.displaySmall` with `testTag("twitch-code")`, an `OutlinedButton(OPEN_BROWSER)` calling `onOpenBrowser(verificationUri)`, `WAITING` in `bodySmall`; button `CANCEL`.
  - `Connected`: `connected(name)`; button `DONE`.
  - `Failed`: the message; buttons `TRY_AGAIN` (→ `onTryAgain`) and `CANCEL`.
- [ ] `ConnectDialog(kind: AccountKind, account: Account?, openBrowser: BrowserOpener, onClose: () -> Unit)`: when `account is TwitchAccount` → a private composable that holds `var state by remember { mutableStateOf<TwitchSignInState>(Starting) }`, `var attempt by remember { mutableStateOf(0) }`, runs `LaunchedEffect(attempt) { withContext(Dispatchers.IO) { TwitchSignIn(account).run { state = it } } }`, opens the browser once per attempt when the state first becomes `WaitingForApproval` (`LaunchedEffect(state)` with a `remember`ed "opened for attempt" marker), and renders `TwitchConnectDialog`. Otherwise the existing placeholder (keep its text for start.gg / YouTube).
- [ ] `AppRoot`: new parameter `openBrowser: BrowserOpener` (after `saves`). Status loading uses `checkStatus()` instead of `status()` (still `runCatching`, still `Dispatchers.IO`). The dialog call becomes `ConnectDialog(kind, accounts.firstOrNull { it.kind == kind }, openBrowser) { connecting = null; refreshCount++ }`.
- [ ] `Main.kt`: `val twitchAuth = if (info.twitchConfigured) TwitchAuth(info.twitchClientId, JdkHttpClient(), SystemClock) else null`; `Accounts.create(info, KeyringSecretStore(), twitchAuth)`; pass `SystemBrowserOpener` to `AppRoot`.
- [ ] Update every `AppRoot(...)` call in tests to pass a `BrowserOpener` (a recording fake: `val opened = mutableListOf<String>(); val browser = BrowserOpener { opened += it }`).
- [ ] `TwitchConnectDialogTest`: `shows the code and opens the browser` (state Waiting → code text visible; click `OPEN_BROWSER` → callback got the uri), `connected shows the channel name`, `failure offers a new code` (click `TRY_AGAIN` → callback).
- [ ] `AppRootTest` — build accounts with `Accounts.create(info, store, TwitchAuth("cid", fakeHttp, Clock { 1_000 }))`, where `fakeHttp` answers device (interval **0**, so polling is instant), token (200), users (200 `FGCHamburg`), validate (200):
  - `connect twitch shows the code and opens the browser` — click the first "Connect" → wait for `twitch-code` with text `ABCDEFGH`; `opened == [verification uri]`.
  - `after approving twitch setup shows the channel name` — device+token approve immediately → dialog shows `Connected as FGCHamburg` → click `Done` → Setup row shows `Connected as FGCHamburg`.
  - `lost twitch access asks to reconnect` — store a Twitch credential with `expiresAtEpochSeconds = 0` and make refresh answer 400 → on start the row shows `Access was lost. Reconnect to keep using fgchamburg.` and a `Reconnect` button.
  - Keep the existing `connect opens the sign-in dialog` test working (it uses the default accounts without `twitchAuth`, so it still shows the placeholder; adjust it to click start.gg's Connect if needed).
- [ ] `UiSnapshots`: keep existing files; `08-connect-dialog.png` now shows the start.gg placeholder (`ConnectDialog` for STARTGG via the placeholder path — call the placeholder through `ConnectDialog(AccountKind.STARTGG, null, {}) {}`). Add `12-twitch-code.png` (Setup with none + `TwitchConnectDialog(WaitingForApproval("ABCDEFGH", "https://www.twitch.tv/activate?device-code=ABCDEFGH"), …)`), `13-twitch-connected.png` (`Connected("FGCHamburg")`), `14-setup-twitch-reconnect.png` (Setup with Twitch `NeedsReconnect("FGCHamburg")`, start.gg Connected), `15-twitch-code-expired.png` (`Failed(TwitchMessages.CODE_EXPIRED)`).
- [ ] `make check` passes. Commit `FGC-5: connect twitch from setup`.

### Task 7: Docs (Haiku)

**Files:** `README.md`, `.env.example`.

- [ ] README: new section `## Twitch sign-in` after `## Develop`: one-time registration summary (dev.twitch.tv/console → Register Your Application; OAuth Redirect URL `http://localhost`; Category Application Integration; **Client Type: Public**, can't be changed later; copy the Client ID; needs two-factor on the owning account), where the key goes (`TWITCH_CLIENT_ID` in `.env` via `make setup`, and the GitHub Actions secret for releases), what users see (code + browser approval), scope used (`user:read:broadcast`, why), and that without the key the Twitch row says "Not available in this build of the app". Link the Linear doc "Setup: Register the Twitch app" (https://linear.app/xunfosechunfos/document/setup-register-the-twitch-app-37eb7504fd7e).
- [ ] `.env.example`: comment above `TWITCH_CLIENT_ID` → `# Twitch sign-in (public client, no secret). See README "Twitch sign-in".`
- [ ] Commit `FGC-5: document the twitch app registration`.

## Manual checks

- With a real `TWITCH_CLIENT_ID` in `.env`: `make run` → Setup → Twitch Connect → code shown, browser opens on twitch.tv/activate with the code → Authorize → dialog says "Connected as <channel>" → Done → row shows it. Quit and relaunch: still connected. Revoke the app at twitch.tv/settings/connections, relaunch: row says access was lost and offers Reconnect.
- Needs a person to approve in a browser; if not done, the PR says so.

## Out of scope

- Listing VODs (FGC-8) — it will use `TwitchAccount.accessToken()` / `forceRefresh()` and `auth.clientIdValue`.
- Hourly re-validation while the app stays open (Twitch asks apps to validate periodically). Start-up validation plus refresh-on-use covers what this ticket asks; FGC-8's API calls will meet a 401 and refresh then.
- yt-dlp login for sub-only VODs (FGC-10).
- start.gg and YouTube sign-in (FGC-6, FGC-7); they keep the placeholder dialog.
