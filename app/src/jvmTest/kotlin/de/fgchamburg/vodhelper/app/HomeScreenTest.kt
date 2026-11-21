package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    private val info = AppInfo.from(version = "1.2.3")
    private val ready = mapOf(
        AccountKind.TWITCH to AccountStatus.Connected("a"),
        AccountKind.STARTGG to AccountStatus.Connected("b"),
        AccountKind.YOUTUBE to AccountStatus.NotConnected,
    )

    @Test
    fun `shows name and version`() = runComposeUiTest {
        setContent { HomeScreen(info, ready, {}, {}, null) }

        onNodeWithText("FGC VOD Helper").assertExists()
        onNodeWithText("Version 1.2.3").assertExists()
    }

    @Test
    fun `new project is disabled while a required account is missing`() = runComposeUiTest {
        setContent { HomeScreen(info, ready + (AccountKind.STARTGG to AccountStatus.NotConnected), {}, {}, null) }

        onNodeWithText("New project").assertIsNotEnabled()
        onNodeWithText("Connect start.gg to start a project.").assertExists()
    }

    @Test
    fun `new project is enabled when ready`() = runComposeUiTest {
        var calls = 0
        setContent { HomeScreen(info, ready, { calls++ }, {}, null) }

        onNodeWithText("New project").assertIsEnabled().performClick()
        assertEquals(1, calls)
    }

    @Test
    fun `settings button calls back`() = runComposeUiTest {
        var calls = 0
        setContent { HomeScreen(info, ready, {}, { calls++ }, null) }

        onNodeWithText("Settings").performClick()
        assertEquals(1, calls)
    }

    @Test
    fun `shows the notice`() = runComposeUiTest {
        setContent { HomeScreen(info, ready, {}, {}, "Creating projects comes in a later update.") }

        onNodeWithText("Creating projects comes in a later update.").assertExists()
    }
}
