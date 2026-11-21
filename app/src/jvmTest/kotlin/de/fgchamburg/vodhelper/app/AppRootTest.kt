package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.accounts.Account
import de.fgchamburg.vodhelper.core.accounts.AccountCredential
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.Accounts
import de.fgchamburg.vodhelper.core.accounts.InMemorySecretStore
import de.fgchamburg.vodhelper.core.settings.AppSettings
import de.fgchamburg.vodhelper.core.settings.InMemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppRootTest {
    private val info = AppInfo.from("1.2.3", twitchClientId = "x", googleClientId = "g", googleClientSecret = "s")
    private val store = InMemorySecretStore()
    private val accounts = Accounts.create(info, store)
    private val settings = InMemorySettingsStore()

    private fun connect(kind: AccountKind, name: String) =
        accounts.first { it.kind == kind }.save(AccountCredential(name, "secret", null))

    private fun ComposeUiTest.start() {
        setContent { AppRoot(info, tools = null, accounts = accounts, settings = settings) }
        waitForStatuses()
    }

    private fun ComposeUiTest.waitForStatuses() =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Checking accounts…").fetchSemanticsNodes().isEmpty() }

    @Test
    fun `first launch shows setup with all three accounts`() = runComposeUiTest {
        start()

        onNodeWithText("Welcome to FGC VOD Helper").assertExists()
        for (kind in AccountKind.entries) onNodeWithText(kind.label).assertExists()
    }

    @Test
    fun `connect opens the sign-in dialog`() = runComposeUiTest {
        start()

        onAllNodesWithText("Connect")[0].performClick()
        onNodeWithText("Connect Twitch").assertExists()
        onNodeWithText("OK").performClick()
        waitForStatuses()
        onAllNodesWithText("Connect Twitch").assertCountEquals(0)
    }

    @Test
    fun `sign out disconnects the account`() = runComposeUiTest {
        connect(AccountKind.TWITCH, "fgchamburg")
        start()

        onNodeWithText("Connected as fgchamburg").assertExists()
        onNodeWithText("Sign out").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Connected as fgchamburg").fetchSemanticsNodes().isEmpty() }
        assertNull(store.get("twitch"))
    }

    @Test
    fun `continue marks setup done and opens home`() = runComposeUiTest {
        connect(AccountKind.TWITCH, "a")
        connect(AccountKind.STARTGG, "b")
        start()

        onNodeWithText("Continue").performClick()

        onNodeWithText("New project").assertExists()
        assertTrue(settings.current.setupDone)
    }

    @Test
    fun `after setup the app opens on the home screen`() = runComposeUiTest {
        settings.current = AppSettings(setupDone = true)
        start()

        onNodeWithText("New project").assertExists()
        onAllNodesWithText("Welcome to FGC VOD Helper").assertCountEquals(0)
    }

    @Test
    fun `settings shows the accounts`() = runComposeUiTest {
        settings.current = AppSettings(setupDone = true)
        start()

        onNodeWithText("Settings").performClick()
        waitForStatuses()
        onNodeWithText("Accounts").assertExists()
        onNodeWithText("Twitch").assertExists()
        onNodeWithText("Video tools").assertExists()

        onNodeWithText("Back").performClick()
        onNodeWithText("New project").assertExists()
    }
}
