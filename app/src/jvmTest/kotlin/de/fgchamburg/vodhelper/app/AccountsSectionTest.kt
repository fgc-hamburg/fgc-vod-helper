package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.accounts.AccountAction
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AccountsSectionTest {
    private val none = mapOf(
        AccountKind.TWITCH to AccountStatus.NotConnected,
        AccountKind.STARTGG to AccountStatus.NotConnected,
        AccountKind.YOUTUBE to AccountStatus.NotConnected,
    )

    @Test
    fun `lists the three accounts with requirements`() = runComposeUiTest {
        setContent { AccountsSection(none) { _, _ -> } }

        onNodeWithText("Accounts").assertIsDisplayed()
        onNodeWithText("Twitch").assertIsDisplayed()
        onNodeWithText("start.gg").assertIsDisplayed()
        onNodeWithText("YouTube").assertIsDisplayed()
        onAllNodesWithText("Required").assertCountEquals(2)
        onNodeWithText("Optional: only needed to upload to YouTube").assertIsDisplayed()
    }

    @Test
    fun `connected account shows its name with reconnect and sign out`() = runComposeUiTest {
        setContent { AccountsSection(none + (AccountKind.TWITCH to AccountStatus.Connected("fgchamburg"))) { _, _ -> } }

        onNodeWithText("Connected as fgchamburg").assertIsDisplayed()
        onNodeWithText("Reconnect").assertIsDisplayed()
        onNodeWithText("Sign out").assertIsDisplayed()
    }

    @Test
    fun `not connected account shows connect`() = runComposeUiTest {
        setContent { AccountsSection(none) { _, _ -> } }

        onAllNodesWithText("Not connected").assertCountEquals(3)
        onAllNodesWithText("Connect").assertCountEquals(3)
    }

    @Test
    fun `not available account shows no buttons`() = runComposeUiTest {
        setContent { AccountsSection(none + (AccountKind.YOUTUBE to AccountStatus.NotConfigured)) { _, _ -> } }

        onNodeWithText("Not available in this build of the app").assertIsDisplayed()
        onAllNodesWithText("Connect").assertCountEquals(2)
    }

    @Test
    fun `clicking a button reports the account and action`() = runComposeUiTest {
        val clicks = mutableListOf<Pair<AccountKind, AccountAction>>()
        setContent {
            AccountsSection(none + (AccountKind.STARTGG to AccountStatus.Connected("x"))) { k, a -> clicks += k to a }
        }

        onNode(hasText("Connect") and hasAnyAncestor(hasTestTag("account-twitch"))).performClick()
        onNode(hasText("Sign out") and hasAnyAncestor(hasTestTag("account-startgg"))).performClick()

        assertEquals(
            listOf(AccountKind.TWITCH to AccountAction.CONNECT, AccountKind.STARTGG to AccountAction.SIGN_OUT),
            clicks,
        )
    }

    @Test
    fun `shows checking while statuses load`() = runComposeUiTest {
        setContent { AccountsSection(null) { _, _ -> } }

        onNodeWithText("Checking accounts…").assertIsDisplayed()
        onAllNodesWithText("Connect").assertCountEquals(0)
    }
}
