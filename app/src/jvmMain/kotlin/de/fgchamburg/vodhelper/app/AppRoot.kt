package de.fgchamburg.vodhelper.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.accounts.Account
import de.fgchamburg.vodhelper.core.accounts.AccountAction
import de.fgchamburg.vodhelper.core.accounts.AccountKind
import de.fgchamburg.vodhelper.core.accounts.AccountStatus
import de.fgchamburg.vodhelper.core.project.CreateResult
import de.fgchamburg.vodhelper.core.project.LoadResult
import de.fgchamburg.vodhelper.core.project.Project
import de.fgchamburg.vodhelper.core.project.ProjectMessages
import de.fgchamburg.vodhelper.core.project.ProjectSession
import de.fgchamburg.vodhelper.core.project.ProjectStore
import de.fgchamburg.vodhelper.core.project.SaveState
import de.fgchamburg.vodhelper.core.settings.AppSettings
import de.fgchamburg.vodhelper.core.settings.RecentProject
import de.fgchamburg.vodhelper.core.settings.RecentProjects
import de.fgchamburg.vodhelper.core.settings.SettingsStore
import de.fgchamburg.vodhelper.core.settings.update
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { SETUP, HOME, SETTINGS, NEW_PROJECT, PROJECT }

/**
 * Holds navigation, account and project state; the screens themselves are stateless.
 * Every write to disk (project and settings) goes through [saves], queued at the moment of the
 * change, so writes land in order and aren't lost when the screen goes away or the app quits.
 */
@Composable
fun AppRoot(
    info: AppInfo,
    tools: VideoToolsReport?,
    accounts: List<Account>,
    settings: SettingsStore,
    projects: ProjectStore,
    pickFolder: FolderPicker,
    saves: SaveQueue,
) {
    val initialSettings = remember { settings.load() }
    var screen by remember { mutableStateOf(if (initialSettings.setupDone) Screen.HOME else Screen.SETUP) }
    var statuses by remember { mutableStateOf<Map<AccountKind, AccountStatus>?>(null) }
    var refreshCount by remember { mutableStateOf(0) }
    var connecting by remember { mutableStateOf<AccountKind?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf(initialSettings.recentProjects) }
    var lastFolder by remember { mutableStateOf(initialSettings.lastProjectsFolder) }
    var session by remember { mutableStateOf<ProjectSession?>(null) }
    var project by remember { mutableStateOf<Project?>(null) }
    var saveState by remember { mutableStateOf<SaveState>(SaveState.Saved) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf<String?>(null) }
    var newError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // If the settings file can't be written, what's on screen is still right for this run.
    fun writeSettings(change: (AppSettings) -> AppSettings) {
        saves.submit { runCatching { settings.update(change) } }
    }

    fun setRecent(list: List<RecentProject>) {
        recent = list
        writeSettings { it.copy(recentProjects = list) }
    }

    fun startSession(folder: String, opened: Project) {
        session = ProjectSession(projects, folder, opened)
        project = opened
        saveState = SaveState.Saved
        nameError = null
        screen = Screen.PROJECT
    }

    // Keychain reads can block, so they stay off the UI thread. A password store that can't be
    // read (locked, access refused) shows the account as not connected instead of crashing.
    LaunchedEffect(refreshCount) {
        statuses = withContext(Dispatchers.IO) {
            accounts.associate { it.kind to (runCatching { it.status() }.getOrNull() ?: AccountStatus.NotConnected) }
        }
    }

    val onAction: (AccountKind, AccountAction) -> Unit = { kind, action ->
        when (action) {
            AccountAction.CONNECT, AccountAction.RECONNECT -> connecting = kind
            AccountAction.SIGN_OUT -> scope.launch {
                withContext(Dispatchers.IO) { runCatching { accounts.first { it.kind == kind }.signOut() } }
                refreshCount++
            }
        }
    }

    AppTheme {
        when (screen) {
            Screen.SETUP -> SetupScreen(
                statuses,
                onAction,
                onContinue = {
                    screen = Screen.HOME
                    // If the settings file can't be written, setup simply shows again next launch.
                    writeSettings { it.copy(setupDone = true) }
                },
            )
            Screen.HOME -> HomeScreen(
                info,
                statuses,
                recent,
                onNewProject = {
                    notice = null
                    newName = ""
                    newError = null
                    newFolder = lastFolder
                    screen = Screen.NEW_PROJECT
                },
                onOpenRecent = { entry ->
                    notice = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { projects.load(entry.folder) }.getOrElse { LoadResult.Unreadable(it.message ?: "") }
                        }
                        if (result is LoadResult.Loaded) {
                            startSession(entry.folder, result.project)
                            setRecent(RecentProjects.opened(recent, RecentProject(entry.folder, result.project.name)))
                        } else {
                            notice = ProjectMessages.cannotOpen(entry.name, result)
                        }
                    }
                },
                onRemoveRecent = { entry -> setRecent(RecentProjects.removed(recent, entry.folder)) },
                onOpenSettings = {
                    notice = null
                    screen = Screen.SETTINGS
                },
                notice = notice,
            )
            Screen.SETTINGS -> SettingsScreen(info, statuses, tools, onAction, onBack = { screen = Screen.HOME })
            Screen.NEW_PROJECT -> NewProjectScreen(
                name = newName,
                folder = newFolder,
                error = newError,
                onNameChange = {
                    newName = it
                    newError = null
                },
                onChooseFolder = {
                    // The system dialog is modal, so it is called straight from the click.
                    pickFolder.pick(ProjectMessages.CHOOSE_FOLDER_TITLE, newFolder)?.let {
                        newFolder = it
                        newError = null
                    }
                },
                onCreate = {
                    val parent = newFolder
                    if (parent != null && !creating) {
                        creating = true
                        val name = newName
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { projects.create(parent, name) }
                                    .getOrElse { CreateResult.Refused(ProjectMessages.couldNotCreate(it.message ?: "unknown error")) }
                            }
                            creating = false
                            when (result) {
                                is CreateResult.Refused -> newError = result.message
                                is CreateResult.Created -> {
                                    startSession(result.folder, result.project)
                                    val list = RecentProjects.opened(recent, RecentProject(result.folder, result.project.name))
                                    recent = list
                                    lastFolder = parent
                                    writeSettings { it.copy(recentProjects = list, lastProjectsFolder = parent) }
                                }
                            }
                        }
                    }
                },
                onCancel = { screen = Screen.HOME },
                creating = creating,
            )
            Screen.PROJECT -> {
                val open = session
                val current = project
                if (open != null && current != null) {
                    ProjectScreen(
                        project = current,
                        folder = open.folder,
                        saveState = saveState,
                        onRename = { newProjectName ->
                            // The text field follows the keys at once; the save is queued right now.
                            project = current.copy(name = newProjectName)
                            if (newProjectName.isBlank()) {
                                // A blank name is shown while typing but never saved.
                                nameError = ProjectMessages.NAME_REQUIRED
                            } else {
                                nameError = null
                                val saved = saves.submit { open.update { it.copy(name = newProjectName) } }
                                setRecent(RecentProjects.opened(recent, RecentProject(open.folder, newProjectName)))
                                scope.launch {
                                    val state = saved.await()
                                    if (session === open) saveState = state
                                }
                            }
                        },
                        onClose = {
                            session = null
                            project = null
                            screen = Screen.HOME
                        },
                        nameError = nameError,
                    )
                }
            }
        }
        connecting?.let { kind ->
            ConnectDialog(kind) {
                connecting = null
                refreshCount++
            }
        }
    }
}
