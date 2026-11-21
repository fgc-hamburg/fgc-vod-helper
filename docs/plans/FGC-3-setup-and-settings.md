# FGC-3: First-time setup screen and settings — Plan

> For agentic workers: built task by task via `implement-ticket` (one subagent per task, in order). Steps use checkboxes.

**Ticket:** FGC-3 — First-time setup screen and settings — https://linear.app/xunfosechunfos/issue/FGC-3/first-time-setup-screen-and-settings

**Goal:** On first launch the app shows a setup screen listing Twitch (required), start.gg (required) and YouTube (optional), each with its connection status and Connect / Reconnect / Sign out buttons. The user can't continue to a project until the required accounts are connected. The same accounts list is in Settings. Sign-in details live only in the OS password store.

**Spec:** `docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md` ("Goals" 1, "How it works → First-time setup", "Parts → Setup & accounts", "Left to the builder").

**Tech stack:** as FGC-2, plus `com.github.javakeyring:java-keyring:1.0.4` (core jvmMain), `org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0` (core commonMain) with the Kotlin serialization plugin (`org.jetbrains.kotlin.plugin.serialization`, version = `kotlin`).

## Acceptance checks

| # | Scope bullet | Verified by |
|---|---|---|
| 1 | On first launch, a setup screen lists Twitch (required), start.gg (required) and YouTube (optional) | `AppRootTest.first launch shows setup with all three accounts`; `AccountMessagesTest.requirement`; snapshot `01-setup-first-launch.png` |
| 2 | Each account shows whether it is connected, with buttons to connect, reconnect or sign out | `AccountsSectionTest` (connected / not connected / not available); `StoredAccountTest`; `AppRootTest.sign out disconnects the account`; `AppRootTest.connect opens the sign-in dialog`; snapshots `01`, `02` |
| 3 | The user can't start a project until the required accounts are connected | `AccountsOverviewTest`; `HomeScreenTest.new project is disabled while a required account is missing`; `SetupScreenTest.setup says which accounts are needed but can be continued`; `AccountMessagesTest.hint says when an account is not available in this build`; snapshot `04-home-missing-account.png` |
| 4 | The same screen is reachable later from settings | `AppRootTest.settings shows the accounts`; `AppRootTest.after setup the app opens on the home screen`; snapshot `05-settings.png` |
| 5 | Sign-in details are stored safely by the computer's password store, never in project files | `StoredAccountTest.save writes only to the secret store`; `FileSettingsStoreTest.settings file holds no sign-in details`; `KeyringSecretStoreTest` (real Keychain / Credential Manager round trip, runs on both CI OSes); `Main.kt` wires `KeyringSecretStore` |

## Approach

- **Accounts in `core` (`de.fgchamburg.vodhelper.core.accounts`).**
  - `AccountKind` enum in this fixed order: `TWITCH("Twitch", required = true, secretKey = "twitch")`, `STARTGG("start.gg", true, "startgg")`, `YOUTUBE("YouTube", false, "youtube")`.
  - `AccountStatus`: `NotConfigured` (this build has no client keys for the service, e.g. Twitch without `TWITCH_CLIENT_ID`), `NotConnected`, `Connected(accountName)`.
  - `Account` interface: `kind`, `status()`, `save(credential)` (connect: what each service's sign-in calls when it succeeds), `signOut()`. Agent notes asked for status/connect/disconnect; the sign-in *flow* differs per service (device code, pasted token, browser) and needs UI, so the core interface takes the finished credential and the flow is a dialog in `app` (see below).
  - `StoredAccount` is the one implementation: it keeps an `AccountCredential(accountName, secret, refreshSecret?)` as JSON in the `SecretStore` under `kind.secretKey`. Unreadable entry → `NotConnected`. Service tickets (FGC-5/6/7) add the sign-in flow and may add credential fields with defaults.
  - `Accounts.create(info: AppInfo, store: SecretStore): List<Account>` builds all three in enum order; `configured` = `info.twitchConfigured` / `true` (start.gg needs no build key) / `info.youtubeConfigured`.
  - `AccountsOverview(statuses)` answers `missingRequired` and `canStartProject`. `AccountMessages` holds every user-facing string, so wording is tested in plain Kotlin.
- **Secrets.** `SecretStore` interface (commonMain: `get`, `put`, `delete`). `KeyringSecretStore` (jvmMain) wraps java-keyring with service name `"FGC VOD Helper"` (tests use `"FGC VOD Helper (test)"`). java-keyring: macOS Keychain via jkeychain, Windows Credential Manager via JNA, BSD-3 license. Secrets never go to files or logs; exceptions from the store must not include the secret.
- **Non-secret settings.** `AppSettings(setupDone: Boolean = false)`, `@Serializable`. `SettingsStore` interface (`load`, `save`). `FileSettingsStore(dir)` reads/writes `<dir>/settings.json`, written atomically (temp file in the same dir + `ATOMIC_MOVE`); missing or broken file → defaults. `AppDataDir.resolve(os, userHome, appDataEnv)`: macOS `~/Library/Application Support/FGC VOD Helper`, Windows `%APPDATA%\FGC VOD Helper` (fallback `<userHome>\AppData\Roaming\FGC VOD Helper` when `APPDATA` is unset).
- **"First launch"** = `setupDone` is false. Setup's **Continue** sets `setupDone = true` and goes to Home. *Changed after self-review:* Continue is always enabled (originally only when `canStartProject`). Until FGC-5/6 add real sign-in nothing can be connected, so a gated Continue made every first launch a dead end with Settings unreachable. The rule "can't start a project" is enforced by Home's **New project** instead; Setup still shows the hint. Later launches open Home. Disconnecting a required account later doesn't bring the setup back; Home disables **New project** and says what to connect, and Settings has the accounts.
- **Screens (`app`).** Navigation is a `Screen` enum (`SETUP`, `HOME`, `SETTINGS`) held in `AppRoot`.
  - `AccountsSection`: shared by Setup and Settings. One row per account: name, requirement line, status, buttons.
  - `SetupScreen`: welcome title, intro, `AccountsSection`, hint + **Continue**.
  - `HomeScreen`: app name, version, **New project** (disabled with hint when a required account is missing), **Settings**. Projects don't exist yet (FGC-4): clicking an enabled New project shows "Creating projects comes in a later update."
  - `SettingsScreen`: **Back**, title, `AccountsSection`, `VideoToolsSection` (moved here from the main screen, as FGC-2 planned), version.
  - `ConnectDialog(kind, onClose)`: placeholder until FGC-5/6/7, "Signing in to <name> will be added in a later update." Each service ticket replaces its branch.
  - Account statuses are read on `Dispatchers.IO` (Keychain reads can block) and re-read after Sign out or closing the Connect dialog.
- **The old `AppScreen` is removed.** Its tests move to `HomeScreenTest` / `SettingsScreenTest`.
- **UI snapshots** (`make ui-snapshots`) replace the FGC-2 set with the new screens.

### Exact wording (all in `AccountMessages`)

| Key | Text |
|---|---|
| requirement, required | `Required` |
| requirement, YouTube | `Optional: only needed to upload to YouTube` |
| status `Connected(name)` | `Connected as <name>` |
| status `NotConnected` | `Not connected` |
| status `NotConfigured` | `Not available in this build of the app` |
| buttons | `Connect` (NotConnected), `Reconnect` + `Sign out` (Connected), none (NotConfigured) |
| hint, missing 1 | `Connect start.gg to start a project.` |
| hint, missing 2 | `Connect Twitch and start.gg to start a project.` |
| hint, none missing | no hint (`null`) |
| hint, required account not available in this build | `Twitch isn't available in this build of the app.` (appended after any `Connect …` sentence; added after self-review) |
| accounts heading / loading | `Accounts` / `Checking accounts…` |
| setup title / intro / button | `Welcome to FGC VOD Helper` / `Connect your accounts to get started. You can change them later in Settings.` / `Continue` |
| home buttons / placeholder | `New project`, `Settings` / `Creating projects comes in a later update.` |
| settings title / back | `Settings` / `Back` |
| connect dialog | title `Connect <name>`, body `Signing in to <name> will be added in a later update.`, button `OK` |

Names list for the hint: 1 → `A`, 2 → `A and B`, 3+ → `A, B and C`, in `AccountKind` order.

## Steps

### Task 1: Account model and messages (core, common)

Files:
- `gradle/libs.versions.toml`: add `kotlinx-serialization = "1.13.0"`, library `kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }`, plugin `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`.
- `core/build.gradle.kts`: apply `alias(libs.plugins.kotlin.serialization)`; `commonMain.dependencies { implementation(libs.kotlinx.serialization.json) }`.
- `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/accounts/`: `AccountKind.kt`, `AccountStatus.kt`, `AccountCredential.kt`, `SecretStore.kt`, `Account.kt`, `StoredAccount.kt`, `Accounts.kt`, `AccountsOverview.kt`, `AccountMessages.kt`, `InMemorySecretStore.kt` (in commonMain, not test sources, because `app` tests use it too: a `SecretStore` over a `sortedMapOf<String, String>()`, exposing `val entries: Map<String, String>` read-only).
- `core/src/commonTest/kotlin/de/fgchamburg/vodhelper/core/accounts/`: `StoredAccountTest.kt`, `AccountsTest.kt`, `AccountsOverviewTest.kt`, `AccountMessagesTest.kt`.

Signatures:
```kotlin
enum class AccountKind(val label: String, val required: Boolean, val secretKey: String) {
    TWITCH("Twitch", true, "twitch"), STARTGG("start.gg", true, "startgg"), YOUTUBE("YouTube", false, "youtube")
}

sealed interface AccountStatus {
    data object NotConfigured : AccountStatus
    data object NotConnected : AccountStatus
    data class Connected(val accountName: String) : AccountStatus
}

@Serializable
data class AccountCredential(val accountName: String, val secret: String, val refreshSecret: String? = null) {
    override fun toString() = "AccountCredential(accountName=$accountName, secret=***)" // never print secrets
}

/** The computer's password store. Keys are short names like "twitch"; values are never logged. */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun delete(key: String) // no error when absent
}

interface Account {
    val kind: AccountKind
    fun status(): AccountStatus
    /** Stores a finished sign-in. Called by each service's sign-in flow. */
    fun save(credential: AccountCredential)
    fun signOut()
}

class StoredAccount(override val kind: AccountKind, private val store: SecretStore, private val configured: Boolean) : Account
// status(): !configured -> NotConfigured; store.get(kind.secretKey) null -> NotConnected;
//   JSON decode (Json { ignoreUnknownKeys = true }) ok -> Connected(accountName), fails -> NotConnected.
// save(): store.put(kind.secretKey, json). signOut(): store.delete(kind.secretKey).
// credential(): AccountCredential? (decoded or null) — for the service tickets.

object Accounts { fun create(info: AppInfo, store: SecretStore): List<Account> }

data class AccountsOverview(val statuses: Map<AccountKind, AccountStatus>) {
    val missingRequired: List<AccountKind> // required kinds not Connected, in enum order
    val canStartProject: Boolean           // missingRequired.isEmpty()
}

enum class AccountAction { CONNECT, RECONNECT, SIGN_OUT }

object AccountMessages {
    fun requirement(kind: AccountKind): String
    fun describe(status: AccountStatus): String
    fun actions(status: AccountStatus): List<AccountAction> // CONNECT | RECONNECT, SIGN_OUT | empty
    fun actionLabel(action: AccountAction): String          // "Connect" / "Reconnect" / "Sign out"
    fun startProjectHint(overview: AccountsOverview): String?
    fun connectTitle(kind: AccountKind): String             // "Connect Twitch"
    fun connectNotAvailableYet(kind: AccountKind): String   // "Signing in to Twitch will be added in a later update."
    const val ACCOUNTS_HEADING = "Accounts"
    const val CHECKING_ACCOUNTS = "Checking accounts…"
}
```

Tests (test-first, names exact):
- [ ] `StoredAccountTest`: `not configured when the build has no keys`, `not connected when nothing is stored`, `connected after save shows the account name`, `save writes only to the secret store` (InMemorySecretStore has exactly one entry, key `twitch`, whose value contains the secret), `sign out removes the stored sign-in`, `unreadable entry counts as not connected`, `credential toString hides the secret`.
- [ ] `AccountsTest`: `creates all three accounts in order`, `twitch is not configured without its client id`, `youtube is not configured without google keys`, `start gg is always configured`.
- [ ] `AccountsOverviewTest`: `cannot start a project with nothing connected` (missing = [TWITCH, STARTGG]), `youtube is not required`, `can start a project when twitch and start gg are connected`, `a missing status counts as not connected`.
- [ ] `AccountMessagesTest`: `requirement`, `describe each status`, `actions for each status`, `hint names the missing accounts` (1, 2), `no hint when ready`, `connect dialog texts`.
- [ ] `make check` passes.

### Task 2: Password store and settings file (core, JVM)

Files:
- `gradle/libs.versions.toml`: `java-keyring = "1.0.4"`, library `java-keyring = { module = "com.github.javakeyring:java-keyring", version.ref = "java-keyring" }`.
- `core/build.gradle.kts`: `jvmMain.dependencies { implementation(libs.java.keyring) }`; `jvmTest.dependencies { implementation(kotlin("test")) }` if not already inherited.
- `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/settings/AppSettings.kt`, `SettingsStore.kt`.
- `core/src/jvmMain/kotlin/de/fgchamburg/vodhelper/core/accounts/KeyringSecretStore.kt`.
- `core/src/jvmMain/kotlin/de/fgchamburg/vodhelper/core/settings/FileSettingsStore.kt`, `AppDataDir.kt`.
- Tests: `core/src/jvmTest/kotlin/de/fgchamburg/vodhelper/core/accounts/KeyringSecretStoreTest.kt`, `core/src/jvmTest/kotlin/de/fgchamburg/vodhelper/core/settings/FileSettingsStoreTest.kt`, `AppDataDirTest.kt`; `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/settings/InMemorySettingsStore.kt`.

Signatures:
```kotlin
@Serializable
data class AppSettings(val setupDone: Boolean = false)

interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

class InMemorySettingsStore(var current: AppSettings = AppSettings()) : SettingsStore

class FileSettingsStore(private val dir: Path) : SettingsStore {
    val file: Path get() = dir.resolve("settings.json")
    // load: missing/unreadable/invalid JSON -> AppSettings(); Json { ignoreUnknownKeys = true }
    // save: createDirectories(dir); write to a temp file in dir; Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)
}

object AppDataDir {
    const val FOLDER = "FGC VOD Helper"
    fun resolve(os: HostOs, userHome: String, appDataEnv: String?): Path
    fun current(): Path // HostOs.current(); on Linux (null) = <userHome>/.fgc-vod-helper; uses System.getProperty("user.home"), System.getenv("APPDATA")
}

class KeyringSecretStore(private val service: String = SERVICE) : SecretStore {
    companion object { const val SERVICE = "FGC VOD Helper" }
    // Keyring.create() once (lazy). get: getPassword(service, key), PasswordAccessException -> null.
    // put: setPassword. delete: deletePassword, ignore PasswordAccessException (absent).
    // Wrap other failures in SecretStoreException(message without the value).
}
class SecretStoreException(message: String, cause: Throwable?) : Exception(message, cause)
```
`AppDataDir.current()` on Linux (no `HostOs`): `<userHome>/.fgc-vod-helper` — only so dev/CI on Linux doesn't crash; we don't ship Linux.

`InMemorySettingsStore` goes in commonMain (not test sources) because `app` tests can't see `core` test sources.

Tests:
- [ ] `AppDataDirTest`: `mac uses application support`, `windows uses appdata`, `windows falls back when appdata is unset`.
- [ ] `FileSettingsStoreTest` (temp dir): `defaults when the file is missing`, `saves and loads setup done`, `defaults when the file is broken`, `save leaves no temp files`, `settings file holds no sign-in details` (after save, file text equals `{"setupDone":true}`).
- [ ] `KeyringSecretStoreTest` with service `"FGC VOD Helper (test)"` and a key unique per run (`"test-" + System.nanoTime()` is fine here, it's a test): `round trip` (put → get equals → delete → get null), `get of an unknown key is null`, `delete of an unknown key does nothing`. Always delete in `finally`. If `Keyring.create()` fails because the OS has no password store (Linux without a secret service), skip with an early return and print why; on macOS and Windows it must run.
- [ ] `make check` passes (CI runs macOS + Windows, so the Keychain/Credential Manager test runs there).

### Task 3: Screens (app)

Files (all `app/src/jvmMain/kotlin/de/fgchamburg/vodhelper/app/`): `AccountsSection.kt`, `SetupScreen.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `ConnectDialog.kt`, `AppTheme.kt` (the `MaterialTheme` + dark/light wrapper taken from `AppScreen`). Tests in `app/src/jvmTest/kotlin/de/fgchamburg/vodhelper/app/`: `AccountsSectionTest.kt`, `SetupScreenTest.kt`, `HomeScreenTest.kt`, `SettingsScreenTest.kt`.

Signatures (stateless; all strings from `AccountMessages`/plan table):
```kotlin
@Composable fun AppTheme(content: @Composable () -> Unit)
@Composable fun AccountsSection(statuses: Map<AccountKind, AccountStatus>?, onAction: (AccountKind, AccountAction) -> Unit)
@Composable fun SetupScreen(statuses: Map<AccountKind, AccountStatus>?, onAction: (AccountKind, AccountAction) -> Unit, onContinue: () -> Unit)
@Composable fun HomeScreen(info: AppInfo, statuses: Map<AccountKind, AccountStatus>?, onNewProject: () -> Unit, onOpenSettings: () -> Unit, notice: String?)
@Composable fun SettingsScreen(info: AppInfo, statuses: Map<AccountKind, AccountStatus>?, tools: VideoToolsReport?, onAction: (AccountKind, AccountAction) -> Unit, onBack: () -> Unit)
@Composable fun ConnectDialog(kind: AccountKind, onClose: () -> Unit) // AlertDialog
```
- `statuses == null` → `Checking accounts…`; Continue / New project disabled while null.
- Rows in `AccountKind` order. Each row: label (titleMedium), requirement (bodySmall), status (bodyMedium; error color when a *required* account isn't connected), buttons from `AccountMessages.actions`.
- Setup: hint text under the accounts when `startProjectHint` is non-null; Continue enabled only when `canStartProject`.
- Home: New project enabled only when `canStartProject`, hint shown otherwise; `notice` text shown under the buttons when non-null.
- Settings: Back button top-left, then `Settings` title, `AccountsSection`, `VideoToolsSection(tools)`, `info.versionLabel`. Content scrolls vertically (`verticalScroll`) so it fits 1100×720.
- Remove `AppScreen.kt` and `AppScreenTest.kt`; move its four tests' assertions into `HomeScreenTest` (`shows name and version`) and `SettingsScreenTest` (`shows checking while tools are checked`, `shows video tool versions`, `shows a missing tool`). `UiSnapshots.kt` will stop compiling — Task 4 rewrites it; in this task, change it minimally to render `SettingsScreen` so the build stays green.

Tests (compose `runComposeUiTest`, names exact):
- [ ] `AccountsSectionTest`: `lists the three accounts with requirements`, `connected account shows its name with reconnect and sign out`, `not connected account shows connect`, `not available account shows no buttons`, `clicking a button reports the account and action`, `shows checking while statuses load`.
- [ ] `SetupScreenTest`: `continue is disabled until required accounts are connected` (hint `Connect Twitch and start.gg to start a project.` shown), `continue is enabled when twitch and start gg are connected` (YouTube not connected), `continue calls back`.
- [ ] `HomeScreenTest`: `shows name and version`, `new project is disabled while a required account is missing`, `new project is enabled when ready`, `settings button calls back`, `shows the notice`.
- [ ] `SettingsScreenTest`: `shows accounts and video tools`, `back calls back`, plus the three moved tool tests.
- [ ] `make check` passes.

Never screenshot or record the real screen, and don't launch the app to look at it. UI is checked through these tests and `make ui-snapshots`.

### Task 4: Wire it together (app)

Files: `app/src/jvmMain/kotlin/de/fgchamburg/vodhelper/app/AppRoot.kt`, `Main.kt`; `app/src/jvmTest/kotlin/de/fgchamburg/vodhelper/app/AppRootTest.kt`, `UiSnapshots.kt`; `app/build.gradle.kts` (runtime modules, only if needed); `CLAUDE.md`.

```kotlin
enum class Screen { SETUP, HOME, SETTINGS }

@Composable
fun AppRoot(info: AppInfo, tools: VideoToolsReport?, accounts: List<Account>, settings: SettingsStore)
```
- Initial screen: `if (settings.load().setupDone) HOME else SETUP` (read once, `remember`).
- `statuses` state: `Map<AccountKind, AccountStatus>?`, loaded with `withContext(Dispatchers.IO) { accounts.associate { it.kind to it.status() } }` in a `LaunchedEffect(refreshCount)`. Bump `refreshCount` after Sign out and after the Connect dialog closes.
- `onAction`: CONNECT/RECONNECT → `connecting = kind` → `ConnectDialog(kind) { connecting = null; refresh }`. SIGN_OUT → `withContext(IO) { account.signOut() }` then refresh (use `rememberCoroutineScope`).
- Setup Continue → `settings.save(settings.load().copy(setupDone = true))`, screen = HOME. Home Settings → SETTINGS; Settings Back → HOME. Home New project → `notice = "Creating projects comes in a later update."`.
- Everything wrapped in `AppTheme`.

`Main.kt`: `val store = KeyringSecretStore()`, `val accounts = Accounts.create(info, store)`, `val settings = FileSettingsStore(AppDataDir.current())`, `AppRoot(info, tools, accounts, settings)`. Keep `--check-tools` handling unchanged and before any of this.

Tests `AppRootTest` (use `InMemorySecretStore`, `InMemorySettingsStore`, `Accounts.create(AppInfo.from("1.2.3", twitchClientId = "x", googleClientId = "g", googleClientSecret = "s"), store)`; wait for statuses with `waitUntil { onAllNodesWithText("Checking accounts…").fetchSemanticsNodes().isEmpty() }`):
- [ ] `first launch shows setup with all three accounts`
- [ ] `connect opens the sign-in dialog` (click Connect on Twitch → `Connect Twitch` dialog, OK closes it)
- [ ] `sign out disconnects the account` (store pre-filled for Twitch via `account.save`, click Sign out → `Not connected`, store has no `twitch` key)
- [ ] `continue marks setup done and opens home` (Twitch + start.gg saved; click Continue → `New project` visible, `settings.current.setupDone == true`)
- [ ] `after setup the app opens on the home screen` (settings start with `setupDone = true`)
- [ ] `settings shows the accounts` (from Home click Settings → `Accounts`, `Twitch`, `Video tools` visible; Back returns to Home)

`UiSnapshots.kt` — render at 1100×720 through `AppTheme` and the screens with fixed statuses (no real Keychain, no network):
- `01-setup-first-launch.png` (all NotConnected)
- `02-setup-ready.png` (Twitch `Connected("fgchamburg")`, start.gg `Connected("FGC Hamburg")`, YouTube NotConnected)
- `03-connect-dialog.png` (`ConnectDialog(TWITCH)` over the setup screen)
- `04-home-missing-account.png` (start.gg NotConnected)
- `05-settings.png` (all connected, YouTube `Connected("FGC Hamburg")`; tools from `realOrFakeReport()` as today)

Runtime modules: after adding java-keyring, run `./gradlew :app:suggestRuntimeModules` and add any new module it lists to `modules(...)` in `app/build.gradle.kts` (the packaged app has only those).

`CLAUDE.md` → "Code layout": add one bullet: "Accounts: `core/.../accounts` (`Account`, `StoredAccount`, `AccountMessages`). Sign-in details go only through `SecretStore` (`KeyringSecretStore` = macOS Keychain / Windows Credential Manager). Non-secret settings: `settings.json` in the OS app-data folder (`AppDataDir`). Each service ticket adds its sign-in flow in `ConnectDialog`."

- [ ] `make check` passes.
- [ ] `make ui-snapshots TICKET=FGC-3` writes the five PNGs.

## Out of scope

- The actual sign-in for Twitch, start.gg and YouTube (FGC-5, FGC-6, FGC-7). Connect shows a placeholder dialog until then.
- Creating, saving and opening projects (FGC-4). New project only shows a placeholder notice.
- Updating yt-dlp from settings (FGC-11).
- Confirmation before Sign out (not asked for; easy to add if reviewers want it).
- Linux password store support (we don't ship Linux).
