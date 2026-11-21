package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.accounts.Account
import de.fgchamburg.vodhelper.core.accounts.AccountCredential
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.Accounts
import de.fgchamburg.vodhelper.core.accounts.InMemorySecretStore
import de.fgchamburg.vodhelper.core.accounts.SecretStore
import de.fgchamburg.vodhelper.core.project.InMemoryProjectStore
import de.fgchamburg.vodhelper.core.project.Project
import de.fgchamburg.vodhelper.core.settings.AppSettings
import de.fgchamburg.vodhelper.core.settings.InMemorySettingsStore
import de.fgchamburg.vodhelper.core.settings.RecentProject
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
    private val projects = InMemoryProjectStore()
    private val picker = FolderPicker { _, _ -> "/projects" }
    private val saves = SaveQueue()

    private fun connect(kind: AccountKind, name: String) =
        accounts.first { it.kind == kind }.save(AccountCredential(name, "secret", null))

    private fun ComposeUiTest.start() {
        setContent { AppRoot(info, tools = null, accounts = accounts, settings = settings, projects = projects, pickFolder = picker, saves = saves) }
        waitForStatuses()
    }

    private fun ComposeUiTest.waitForStatuses() =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Checking accounts…").fetchSemanticsNodes().isEmpty() }

    @Test
    fun `password store errors show accounts as not connected`() = runComposeUiTest {
        val broken = object : SecretStore {
            override fun get(key: String): String = error("password store locked")
            override fun put(key: String, value: String) = error("password store locked")
            override fun delete(key: String) = error("password store locked")
        }
        val brokenAccounts = Accounts.create(info, broken)
        setContent { AppRoot(info, tools = null, accounts = brokenAccounts, settings = settings, projects = projects, pickFolder = picker, saves = saves) }
        waitForStatuses()

        onAllNodesWithText("Not connected").assertCountEquals(3)
    }

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
        waitUntil(timeoutMillis = 5_000) { settings.current.setupDone }
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

    private fun readyForProjects(recent: List<RecentProject> = emptyList()) {
        connect(AccountKind.TWITCH, "a")
        connect(AccountKind.STARTGG, "b")
        settings.current = AppSettings(setupDone = true, recentProjects = recent)
    }

    private fun ComposeUiTest.createProject(name: String) {
        onNodeWithText("New project").performClick()
        onNodeWithTag("project-name").performTextInput(name)
        onNodeWithText("Choose folder…").performClick()
        onNodeWithText("Create project").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("All changes saved").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `new project creates and opens the project`() = runComposeUiTest {
        readyForProjects()
        start()

        createProject("Weekly")

        assertTrue("/projects/Weekly" in projects.projects)
        waitUntil(timeoutMillis = 5_000) { settings.current.lastProjectsFolder == "/projects" }
        assertEquals(listOf(RecentProject("/projects/Weekly", "Weekly")), settings.current.recentProjects)
        assertEquals("/projects", settings.current.lastProjectsFolder)
    }

    @Test
    fun `creating an existing project shows why`() = runComposeUiTest {
        readyForProjects()
        projects.projects["/projects/Weekly"] = Project(name = "Weekly")
        start()

        onNodeWithText("New project").performClick()
        onNodeWithTag("project-name").performTextInput("Weekly")
        onNodeWithText("Choose folder…").performClick()
        onNodeWithText("Create project").performClick()

        val text = "There is already a project called \"Weekly\" in this folder. " +
            "Open it from Recent projects or pick another name."
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `renaming the project saves it`() = runComposeUiTest {
        readyForProjects()
        start()
        createProject("Weekly")

        onNodeWithTag("project-name").performTextReplacement("Weekly #13")

        waitUntil(timeoutMillis = 5_000) {
            projects.projects["/projects/Weekly"]?.name == "Weekly #13" &&
                settings.current.recentProjects.firstOrNull()?.name == "Weekly #13"
        }
        assertEquals(RecentProject("/projects/Weekly", "Weekly #13"), settings.current.recentProjects.single())
    }

    @Test
    fun `a blank name is not saved`() = runComposeUiTest {
        readyForProjects()
        start()
        createProject("Weekly")

        onNodeWithTag("project-name").performTextReplacement("   ")

        onNodeWithText("Give the project a name.").assertExists()
        saves.awaitIdle()
        assertEquals("Weekly", projects.projects["/projects/Weekly"]?.name)
        assertEquals("Weekly", settings.current.recentProjects.single().name)
    }

    @Test
    fun `fast renames all land, in order`() = runComposeUiTest {
        readyForProjects()
        start()
        createProject("W")

        for (name in listOf("We", "Wee", "Week", "Weekl", "Weekly")) onNodeWithTag("project-name").performTextReplacement(name)
        saves.awaitIdle()

        assertEquals("Weekly", projects.projects["/projects/W"]?.name)
        assertEquals(RecentProject("/projects/W", "Weekly"), settings.current.recentProjects.single())
    }

    @Test
    fun `reopen a recent project`() = runComposeUiTest {
        readyForProjects(listOf(RecentProject("/p/Old", "Old")))
        projects.projects["/p/Old"] = Project(name = "Old")
        start()

        onNodeWithText("Old").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("All changes saved").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project-name").assertTextContains("Old")
        onNodeWithText("/p/Old").assertExists()
    }

    @Test
    fun `a missing recent project shows a message`() = runComposeUiTest {
        readyForProjects(listOf(RecentProject("/p/Gone", "Gone")))
        start()

        onNodeWithText("Gone").performClick()

        val text = "\"Gone\" can't be opened: its folder or project file is gone."
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Gone").assertExists()

        onNodeWithText("Remove from list").performClick()
        waitUntil(timeoutMillis = 5_000) { settings.current.recentProjects.isEmpty() }
        onNodeWithText("No projects yet.").assertExists()
    }

    @Test
    fun `close project returns to the start screen`() = runComposeUiTest {
        readyForProjects()
        start()
        createProject("Weekly")

        onNodeWithText("Close project").performClick()

        onNodeWithText("New project").assertExists()
        onNodeWithText("Recent projects").assertExists()
    }
}
