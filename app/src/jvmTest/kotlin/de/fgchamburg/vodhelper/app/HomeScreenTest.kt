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
import de.fgchamburg.vodhelper.core.settings.RecentProject
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
    private val weekly = RecentProject("/v/Weekly", "Weekly")
    private val monthly = RecentProject("/v/Monthly", "Monthly")

    @Test
    fun `shows name and version`() = runComposeUiTest {
        setContent { HomeScreen(info, ready, emptyList(), {}, {}, {}, {}, null) }

        onNodeWithText("FGC VOD Helper").assertExists()
        onNodeWithText("Version 1.2.3").assertExists()
    }

    @Test
    fun `new project is disabled while a required account is missing`() = runComposeUiTest {
        val missing = ready + (AccountKind.STARTGG to AccountStatus.NotConnected)
        setContent { HomeScreen(info, missing, emptyList(), {}, {}, {}, {}, null) }

        onNodeWithText("New project").assertIsNotEnabled()
        onNodeWithText("Connect start.gg to start a project.").assertExists()
    }

    @Test
    fun `new project is enabled when ready`() = runComposeUiTest {
        var calls = 0
        setContent { HomeScreen(info, ready, emptyList(), { calls++ }, {}, {}, {}, null) }

        onNodeWithText("New project").assertIsEnabled().performClick()
        assertEquals(1, calls)
    }

    @Test
    fun `settings button calls back`() = runComposeUiTest {
        var calls = 0
        setContent { HomeScreen(info, ready, emptyList(), {}, {}, {}, { calls++ }, null) }

        onNodeWithText("Settings").performClick()
        assertEquals(1, calls)
    }

    @Test
    fun `shows the notice`() = runComposeUiTest {
        setContent { HomeScreen(info, ready, emptyList(), {}, {}, {}, {}, "Something to know.") }

        onNodeWithText("Something to know.").assertExists()
    }

    @Test
    fun `lists recent projects`() = runComposeUiTest {
        setContent { HomeScreen(info, ready, listOf(weekly, monthly), {}, {}, {}, {}, null) }

        onNodeWithText("Recent projects").assertExists()
        onNodeWithText("Weekly").assertExists()
        onNodeWithText("/v/Weekly").assertExists()
        onNodeWithText("Monthly").assertExists()
        onNodeWithText("/v/Monthly").assertExists()
    }

    @Test
    fun `no recent projects`() = runComposeUiTest {
        setContent { HomeScreen(info, ready, emptyList(), {}, {}, {}, {}, null) }

        onNodeWithText("No projects yet.").assertExists()
    }

    @Test
    fun `clicking a recent project opens it`() = runComposeUiTest {
        val opened = mutableListOf<RecentProject>()
        setContent { HomeScreen(info, ready, listOf(weekly, monthly), {}, { opened += it }, {}, {}, null) }

        onNodeWithText("Monthly").performClick()
        assertEquals(listOf(monthly), opened)
    }

    @Test
    fun `remove calls back`() = runComposeUiTest {
        val removed = mutableListOf<RecentProject>()
        setContent { HomeScreen(info, ready, listOf(weekly), {}, {}, { removed += it }, {}, null) }

        onNodeWithText("Remove from list").performClick()
        assertEquals(listOf(weekly), removed)
    }

    @Test
    fun `recent projects can be opened while an account is missing`() = runComposeUiTest {
        val opened = mutableListOf<RecentProject>()
        val missing = ready + (AccountKind.STARTGG to AccountStatus.NotConnected)
        setContent { HomeScreen(info, missing, listOf(weekly), {}, { opened += it }, {}, {}, null) }

        onNodeWithText("Weekly").assertIsEnabled().performClick()
        assertEquals(listOf(weekly), opened)
    }
}
