package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.twitch.TwitchMessages
import de.fgchamburg.vodhelper.core.twitch.TwitchSignInState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TwitchConnectDialogTest {
    @Test
    fun `shows the code and opens the browser`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            TwitchConnectDialog(
                TwitchSignInState.WaitingForApproval("ABCDEFGH", "https://www.twitch.tv/activate?device-code=ABCDEFGH"),
                onOpenBrowser = { opened += it },
                onTryAgain = {},
                onClose = {},
            )
        }

        onNodeWithTag("twitch-code").assertTextEquals("ABCDEFGH")
        onNodeWithText(TwitchMessages.OPEN_BROWSER).performClick()
        assertEquals(listOf("https://www.twitch.tv/activate?device-code=ABCDEFGH"), opened)
    }

    @Test
    fun `connected shows the channel name`() = runComposeUiTest {
        setContent { TwitchConnectDialog(TwitchSignInState.Connected("FGCHamburg"), {}, {}, {}) }

        onNodeWithText("Connected as FGCHamburg").assertExists()
        onNodeWithText(TwitchMessages.DONE).assertExists()
    }

    @Test
    fun `failure offers a new code`() = runComposeUiTest {
        var again = 0
        setContent {
            TwitchConnectDialog(TwitchSignInState.Failed(TwitchMessages.CODE_EXPIRED), {}, { again++ }, {})
        }

        onNodeWithText(TwitchMessages.CODE_EXPIRED).assertExists()
        onNodeWithText(TwitchMessages.TRY_AGAIN).performClick()
        assertEquals(1, again)
    }
}
