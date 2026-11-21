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
import de.fgchamburg.vodhelper.core.settings.RecentProject
import de.fgchamburg.vodhelper.core.settings.RecentProjects
import de.fgchamburg.vodhelper.core.settings.SettingsStore
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { SETUP, HOME, SETTINGS, NEW_PROJECT, PROJECT }

/** Holds navigation and account state; the screens themselves are stateless. */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun AppRoot(
    info: AppInfo,
    tools: VideoToolsReport?,
    accounts: List<Account>,
    settings: SettingsStore,
    projects: ProjectStore,
    pickFolder: FolderPicker,
) {
    var screen by remember { mutableStateOf(if (settings.load().setupDone) Screen.HOME else Screen.SETUP) }
    var statuses by remember { mutableStateOf<Map<AccountKind, AccountStatus>?>(null) }
    var refreshCount by remember { mutableStateOf(0) }
    var connecting by remember { mutableStateOf<AccountKind?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf(settings.load().recentProjects) }
    var session by remember { mutableStateOf<ProjectSession?>(null) }
    var project by remember { mutableStateOf<Project?>(null) }
    var saveState by remember { mutableStateOf<SaveState>(SaveState.Saved) }
    var newName by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf<String?>(null) }
    var newError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // One thread for saves, so they happen in order and never overlap.
    val saveDispatcher = remember { Dispatchers.IO.limitedParallelism(1) }

    // Updates the list on screen at once, then writes it to the settings file (read-modify-write).
    // If the file can't be written the list is still right for this run. Runs on the save thread,
    // so writes from fast typing land in order and the last name always wins.
    suspend fun rememberRecent(list: List<RecentProject>, lastFolder: String? = null) {
        recent = list
        withContext(saveDispatcher) {
            runCatching {
                val current = settings.load()
                settings.save(
                    current.copy(recentProjects = list, lastProjectsFolder = lastFolder ?: current.lastProjectsFolder),
                )
            }
        }
    }

    fun startSession(folder: String, opened: Project) {
        session = ProjectSession(projects, folder, opened)
        project = opened
        saveState = SaveState.Saved
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
                    scope.launch { withContext(Dispatchers.IO) { runCatching { settings.save(settings.load().copy(setupDone = true)) } } }
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
                    newFolder = settings.load().lastProjectsFolder
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
                            rememberRecent(RecentProjects.opened(recent, RecentProject(entry.folder, result.project.name)))
                        } else {
                            notice = ProjectMessages.cannotOpen(entry.name, result)
                        }
                    }
                },
                onRemoveRecent = { entry -> scope.launch { rememberRecent(RecentProjects.removed(recent, entry.folder)) } },
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
                    if (parent != null) {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { projects.create(parent, newName) }
                                    .getOrElse { CreateResult.Refused(ProjectMessages.couldNotCreate(it.message ?: "unknown error")) }
                            }
                            when (result) {
                                is CreateResult.Refused -> newError = result.message
                                is CreateResult.Created -> {
                                    startSession(result.folder, result.project)
                                    rememberRecent(
                                        RecentProjects.opened(recent, RecentProject(result.folder, result.project.name)),
                                        lastFolder = parent,
                                    )
                                }
                            }
                        }
                    }
                },
                onCancel = { screen = Screen.HOME },
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
                            // The text field follows the keys at once; the save happens behind it.
                            project = current.copy(name = newProjectName)
                            scope.launch {
                                val state = withContext(saveDispatcher) { open.update { it.copy(name = newProjectName) } }
                                if (session === open) saveState = state
                                rememberRecent(RecentProjects.opened(recent, RecentProject(open.folder, newProjectName)))
                            }
                        },
                        onClose = {
                            session = null
                            project = null
                            screen = Screen.HOME
                        },
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
