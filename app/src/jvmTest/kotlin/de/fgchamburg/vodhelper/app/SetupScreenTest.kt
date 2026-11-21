package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SetupScreenTest {
    private val none = mapOf(
        AccountKind.TWITCH to AccountStatus.NotConnected,
        AccountKind.STARTGG to AccountStatus.NotConnected,
        AccountKind.YOUTUBE to AccountStatus.NotConnected,
    )
    private val ready = none + mapOf(
        AccountKind.TWITCH to AccountStatus.Connected("a"),
        AccountKind.STARTGG to AccountStatus.Connected("b"),
    )

    @Test
    fun `continue is disabled until required accounts are connected`() = runComposeUiTest {
        setContent { SetupScreen(none, { _, _ -> }, {}) }

        onNodeWithText("Welcome to FGC VOD Helper").assertExists()
        onNodeWithText("Connect your accounts to get started. You can change them later in Settings.").assertExists()
        onNodeWithText("Continue").assertIsNotEnabled()
        onNodeWithText("Connect Twitch and start.gg to start a project.").assertExists()
    }

    @Test
    fun `continue is enabled when twitch and start gg are connected`() = runComposeUiTest {
        setContent { SetupScreen(ready, { _, _ -> }, {}) }

        onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun `continue calls back`() = runComposeUiTest {
        var calls = 0
        setContent { SetupScreen(ready, { _, _ -> }, { calls++ }) }

        onNodeWithText("Continue").performClick()
        assertEquals(1, calls)
    }
}
