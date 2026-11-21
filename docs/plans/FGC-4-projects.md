# FGC-4: Create, save and reopen projects — Plan

> For agentic workers: built task by task via `implement-ticket` (one subagent per task, in order). Steps use checkboxes.

**Ticket:** FGC-4 — Create, save and reopen projects — https://linear.app/xunfosechunfos/issue/FGC-4/create-save-and-reopen-projects

**Goal:** The user creates a project (name + folder) from the start screen. Everything in the project is kept in a plain-text `project.json` in the project folder, saved automatically after every change, written so a crash or power loss can never leave a broken file. The start screen lists recent projects; clicking one reopens it.

**Spec:** `docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md` ("Main flow" step 1, "Project file", "Determinism", "Parts → Project store", "Must never happen").

**Tech stack:** as FGC-3. No new libraries (kotlinx-serialization-json is already in core commonMain).

## Acceptance checks

| # | Scope bullet | Verified by |
|---|---|---|
| 1 | The user can create a project with a name and a folder | `ProjectNamesTest`; `FileProjectStoreTest.create makes the project folder and file`, `create refuses a folder that already has a project`, `create refuses a blank name`; `NewProjectScreenTest`; `AppRootTest.new project creates and opens the project`; snapshot `02-new-project.png`; manual: `make run`, create a project, check the folder on disk |
| 2 | The app saves the project after every change, without a save button | `ProjectSessionTest` (every update saves; unchanged update doesn't; failed save is reported and retried on next change); `AppRootTest.renaming the project saves it`; `ProjectScreenTest` (no save button, shows save status); snapshot `03-project.png`; manual: rename in the app, watch `project.json` change |
| 3 | The user can reopen recent projects from the start screen | `RecentProjectsTest`; `FileSettingsStoreTest.saves and loads recent projects`; `HomeScreenTest.lists recent projects`, `clicking a recent project opens it`, `remove calls back`; `AppRootTest.reopen a recent project`, `a missing recent project shows a message`; snapshot `01-home-recent-projects.png`; manual: create, close app, reopen from list |
| 4 | A crash or power loss never leaves a broken project file | `FileProjectStoreTest.a failed write leaves the old file intact`, `leftover temp files are ignored and cleaned up`, `save leaves no temp files`, `an unreadable file is never overwritten by open` |
| 5 | The project file is readable as plain text | `ProjectJsonTest.writes the exact expected text` (golden file: pretty-printed, every field, fixed order, trailing newline), `round trip`, `a newer schema version is refused` |

## Approach

- **Package** `de.fgchamburg.vodhelper.core.project` (new). Model, JSON and session logic in `commonMain`; file access in `jvmMain`.
- **Model** (`Project.kt`), per the design's "Project file" section. Later tickets fill these; this ticket only creates and renames. All `@Serializable`, every property has a fixed declaration order (that is the order in the file):
  ```kotlin
  const val PROJECT_SCHEMA_VERSION = 1

  @Serializable
  data class Project(
      val schemaVersion: Int = PROJECT_SCHEMA_VERSION,
      val name: String,
      val vods: List<Vod> = emptyList(),
      val startggEvent: StartggEvent? = null,
      val matches: List<Match> = emptyList(),
      val descriptionHeader: String = "",
      val exportTitle: String = "",
      val toolVersions: List<ToolVersion> = emptyList(),
  )

  @Serializable
  data class Vod(
      val id: String,               // Twitch video ID
      val title: String,
      val broadcastStart: String,   // ISO-8601 UTC as Twitch gives it, e.g. "2026-11-14T13:00:05Z"
      val lengthMillis: Long,
      val localFile: String? = null, // relative to the project folder, "/" separators
      val download: DownloadState = DownloadState.NOT_STARTED,
  )

  @Serializable
  enum class DownloadState {
      @SerialName("not-started") NOT_STARTED,
      @SerialName("downloading") DOWNLOADING,
      @SerialName("done") DONE,
      @SerialName("failed") FAILED,
      @SerialName("expired") EXPIRED,
  }

  @Serializable
  data class StartggEvent(val url: String, val name: String, val sets: List<StartggSet> = emptyList())

  @Serializable
  data class StartggSet(val id: String, val details: MatchDetails)

  @Serializable
  data class MatchDetails(
      val game: String = "",
      val round: String = "",
      val player1: String = "",
      val player2: String = "",
      val characters1: String = "",
      val characters2: String = "",
  )

  @Serializable
  data class VodPosition(val vodId: String, val millis: Long)

  @Serializable
  data class Match(
      val start: VodPosition,
      val end: VodPosition,
      val details: MatchDetails = MatchDetails(),
      val startggSetId: String? = null,
  )

  @Serializable
  data class ToolVersion(val tool: String, val version: String)
  ```
  Times are whole milliseconds (`Long`), never floating point, so they round-trip exactly. Tool versions are a list, not a map, so the order is the order written. No clock or timestamps of our own in the file.
- **Plain text** (`ProjectJson.kt`): `Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = true }`. `encode(project)` returns the JSON text plus `"\n"`. Every field is always written (even empty/null), so the file shows the whole structure and diffs cleanly. `decode(text): ProjectDecodeResult` reads `schemaVersion` first from the raw `JsonObject`: missing or not an int → `Unreadable`; `> PROJECT_SCHEMA_VERSION` → `TooNew`; else decode → `Ok(project)`; any exception → `Unreadable(reason)`.
  ```kotlin
  sealed interface ProjectDecodeResult {
      data class Ok(val project: Project) : ProjectDecodeResult
      data object TooNew : ProjectDecodeResult
      data class Unreadable(val reason: String) : ProjectDecodeResult
  }
  object ProjectJson {
      const val FILE_NAME = "project.json"
      fun encode(project: Project): String
      fun decode(text: String): ProjectDecodeResult
  }
  ```
- **Folder name** (`ProjectNames.kt`): `fun folderName(name: String): String?`. Trim; replace each of `< > : " / \ | ? *` and control chars (< 0x20) with `-`; trim trailing dots and spaces (Windows); null if the result is empty. Windows reserved names (`CON`, `PRN`, `AUX`, `NUL`, `COM1`–`COM9`, `LPT1`–`LPT9`, case-insensitive, with or without extension) get a trailing `-` appended (`"con"` → `"con-"`). The project name itself is kept exactly as typed (trimmed) inside `project.json`.
- **Where the project lives.** The user picks a folder (e.g. `~/Videos/FGC`); the app creates `<picked folder>/<folderName(name)>/` and writes `project.json` there. Downloads (FGC-10) go into that project folder. This keeps each project in its own folder as the design says, even when the user picks a general folder like Desktop. The last picked folder is remembered in settings and prefilled next time.
- **Store** (`ProjectStore.kt`, commonMain interface; folders are absolute path strings because commonMain has no `Path`):
  ```kotlin
  sealed interface CreateResult {
      data class Created(val folder: String, val project: Project) : CreateResult
      data class Refused(val message: String) : CreateResult
  }
  sealed interface LoadResult {
      data class Loaded(val project: Project) : LoadResult
      data object Missing : LoadResult              // folder or project.json gone
      data object TooNew : LoadResult
      data class Unreadable(val reason: String) : LoadResult
  }
  interface ProjectStore {
      fun create(parentFolder: String, name: String): CreateResult
      fun load(folder: String): LoadResult
      /** Throws on failure; the previous file stays as it was. */
      fun save(folder: String, project: Project)
  }
  ```
  `InMemoryProjectStore` (commonMain, for app tests, like `InMemorySettingsStore`): `val projects: MutableMap<String, Project>` keyed by folder; `create` uses `"$parentFolder/${folderName}"`; refuses blank names and existing keys with the same messages as the file store; `var failSaves = false` makes `save` throw `IllegalStateException("disk full")`.
- **File store** (`FileProjectStore.kt`, jvmMain), `class FileProjectStore internal constructor(private val writeTemp: (Path, ByteArray) -> Unit) : ProjectStore` with `constructor() : this(::writeAndSync)`:
  - `save`: `Files.createTempFile(folder, ".project-", ".json.tmp")`, `writeTemp(tmp, bytes)` (default: `FileChannel` open WRITE, write all bytes, `force(true)`), then `Files.move(tmp, project.json, ATOMIC_MOVE, REPLACE_EXISTING)`; `finally` deletes the temp file if it still exists. On a crash at any point, `project.json` is either the old or the new full file. The test seam `writeTemp` lets a test throw half-way.
  - `load`: folder or file missing → `Missing`; read UTF-8, `ProjectJson.decode`; maps to `Loaded`/`TooNew`/`Unreadable`. Also deletes leftover `.project-*.json.tmp` files in the folder (from a crash) — best effort, errors ignored. `load` never writes `project.json`.
  - `create`: `folderName(name)` null → `Refused(ProjectMessages.NAME_REQUIRED)`; parent folder missing → `Refused(ProjectMessages.folderMissing(parent))`; target folder already has `project.json` → `Refused(ProjectMessages.alreadyExists(folderName))`; else `createDirectories`, `save(Project(name = name.trim()))`, `Created(absolutePath, project)`. Any IOException → `Refused(ProjectMessages.couldNotCreate(e.message))`.
- **Autosave** (`ProjectSession.kt`, commonMain):
  ```kotlin
  sealed interface SaveState {
      data object Saved : SaveState
      data class Failed(val reason: String) : SaveState
  }
  class ProjectSession(private val store: ProjectStore, val folder: String, initial: Project) {
      var project: Project = initial; private set
      var saveState: SaveState = SaveState.Saved; private set
      /** Applies [change] and saves straight away. Returns the new save state. */
      fun update(change: (Project) -> Project): SaveState
  }
  ```
  `update`: `next = change(project)`. If `next == project` and state is `Saved`, return `Saved` without writing. Otherwise set `project = next` (the change is kept in memory even if saving fails), call `store.save`; success → `Saved`; exception → `Failed(e.message ?: e::class.simpleName ?: "unknown error")`. A failed save is retried by the next `update` (even an unchanged one). There is no save button anywhere.
  In the app, every `update` runs on one single-thread dispatcher (`Dispatchers.IO.limitedParallelism(1)`), so saves happen in order and never overlap. Each change is written right away (the file is small); no debounce.
- **Recent projects** (`core/settings`):
  - `AppSettings(setupDone: Boolean = false, recentProjects: List<RecentProject> = emptyList(), lastProjectsFolder: String? = null)`; `@Serializable data class RecentProject(val folder: String, val name: String)`. `FileSettingsStore` keeps `encodeDefaults = false` (default), so the existing `{"setupDone":true}` test still holds.
  - `object RecentProjects { const val MAX = 10; fun opened(list, entry): List<RecentProject>; fun removed(list, folder): List<RecentProject> }`. `opened` puts the entry first, drops any other entry with the same folder, keeps at most `MAX`. Used on create, open and rename (rename updates the name).
- **Messages** (`ProjectMessages.kt`, commonMain), all user-facing project wording (exact text below).
- **Screens (`app`)**. `Screen` enum gains `NEW_PROJECT`, `PROJECT`.
  - `HomeScreen(info, statuses, recent: List<RecentProject>, onNewProject, onOpenRecent: (RecentProject) -> Unit, onRemoveRecent: (RecentProject) -> Unit, onOpenSettings, notice: String?)`. Below the buttons: heading "Recent projects", one row per entry: a text button with the project name (opens it), the folder path in small text under it, and a "Remove from list" text button. Empty list → "No projects yet." The `notice` line stays and now carries open errors. Opening a recent project works even if an account is missing (only New project is gated).
  - `NewProjectScreen(name, folder: String?, error: String?, onNameChange, onChooseFolder, onCreate, onCancel)` — stateless. Title "New project", text field "Project name", folder row: path (or "No folder chosen") + "Choose folder…" button, a hint line `ProjectMessages.willCreate(name)` when it is not null, error line, buttons "Create project" (enabled when `folderName(name) != null && folder != null`) and "Cancel".
  - `ProjectScreen(project: Project, folder: String, saveState: SaveState, onRename: (String) -> Unit, onClose)` — "Close project" button top-left, text field "Project name" bound to `project.name`, folder path, save status line (`ProjectMessages.saveStatus(saveState)`), and the placeholder "Picking VODs comes in a later update." No save button. An all-blank name is allowed while typing (kept as typed in the file); the folder never changes on rename.
  - `FolderPicker` (`fun interface FolderPicker { fun pick(title: String, startIn: String?): String? }`) passed into `AppRoot` so tests use a fake. `SystemFolderPicker` (app, jvmMain): macOS → `java.awt.FileDialog` with system property `apple.awt.fileDialogForDirectories=true` set only during the call; others → `JFileChooser` with `DIRECTORIES_ONLY`. Returns the absolute path or null. Called from the UI thread (it's modal), not tested automatically; manual check.
  - `AppRoot(info, tools, accounts, settings, projects: ProjectStore, pickFolder: FolderPicker)`. Holds `session: ProjectSession?` plus a Compose `project`/`saveState` state mirrored from the session after each update. Create, open, rename and settings writes go through `withContext(saveDispatcher)` (rename) or `Dispatchers.IO` (create/open/settings), wrapped in `runCatching` like FGC-3 does for settings.
    - New project → `NEW_PROJECT` with name "" and folder = `settings.lastProjectsFolder`. Create → `projects.create`; `Refused` shows the message on the form; `Created` → session, recent list `opened`, `lastProjectsFolder` saved, screen `PROJECT`.
    - Open recent → `projects.load(folder)`; `Loaded` → session, recent `opened(folder, project.name)`, `PROJECT`. `Missing`/`TooNew`/`Unreadable` → stay on Home with `ProjectMessages.cannotOpen(entry.name, result)` as the notice; the entry stays in the list (the user can remove it).
    - Rename → `session.update { it.copy(name = new) }`, then recent `opened(folder, new)`.
    - Close project → `HOME` (session dropped; everything is already saved).
    - Remove from list → recent `removed`.
    - `NEW_PROJECT_NOTICE` placeholder is deleted.
  - `Main.kt` passes `FileProjectStore()` and `SystemFolderPicker`.
- **Closing the app mid-save**: the rename is atomic, so the file is old-or-new, never broken. A keystroke typed in the last few milliseconds before quitting could be lost; accepted.

### Exact wording (all in `ProjectMessages`)

| Key | Text |
|---|---|
| `RECENT_HEADING` | `Recent projects` |
| `NO_RECENT` | `No projects yet.` |
| `REMOVE_RECENT` | `Remove from list` |
| `NEW_PROJECT_TITLE` | `New project` |
| `NAME_LABEL` | `Project name` |
| `NO_FOLDER` | `No folder chosen` |
| `CHOOSE_FOLDER` | `Choose folder…` |
| `CHOOSE_FOLDER_TITLE` (picker title) | `Choose where to keep your projects` |
| `CREATE` / `CANCEL` | `Create project` / `Cancel` |
| `willCreate(name)` | `The project will be saved in a new folder: <folderName(name)>` (null when `folderName(name)` is null) |
| `NAME_REQUIRED` | `Give the project a name.` |
| `folderMissing(folder)` | `The folder <folder> doesn't exist any more. Choose another one.` |
| `alreadyExists(folderName)` | `There is already a project called "<folderName>" in this folder. Open it from Recent projects or pick another name.` |
| `couldNotCreate(reason)` | `The project couldn't be created: <reason>` |
| `CLOSE_PROJECT` | `Close project` |
| `saveStatus(Saved)` | `All changes saved` |
| `saveStatus(Failed(r))` | `Couldn't save: <r>. Your changes are kept and will be saved with your next change.` |
| `NEXT_STEP_PLACEHOLDER` | `Picking VODs comes in a later update.` |
| `cannotOpen(name, Missing)` | `"<name>" can't be opened: its folder or project file is gone.` |
| `cannotOpen(name, TooNew)` | `"<name>" was saved by a newer version of the app. Update the app to open it.` |
| `cannotOpen(name, Unreadable)` | `"<name>" can't be opened: its project file is damaged. The file was left as it is.` |

(`willCreate` names only the new folder, not a full path, so the wording is the same on both OSes.)

## Steps

### Task 1: Project model and plain-text format (core, common)

Files:
- Create `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/project/Project.kt` (all model classes above, `PROJECT_SCHEMA_VERSION`).
- Create `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/project/ProjectJson.kt` (`ProjectJson`, `ProjectDecodeResult`).
- Create `core/src/commonTest/kotlin/de/fgchamburg/vodhelper/core/project/ProjectJsonTest.kt`.

- [ ] Write `ProjectJsonTest` first:
  - `writes the exact expected text`: encode `sample` (below) and `assertEquals` against a raw-string golden literal in the test (4-space pretty print as kotlinx produces, every field present, `null` for `startggEvent` in a second smaller case, ends with `"\n"`). Build the golden string by hand from the field order; do not generate it from the code under test.
  - `a new project writes every field`: `ProjectJson.encode(Project(name = "Hamburg Weekly #12"))` equals the exact text:
    ```
    {
        "schemaVersion": 1,
        "name": "Hamburg Weekly #12",
        "vods": [],
        "startggEvent": null,
        "matches": [],
        "descriptionHeader": "",
        "exportTitle": "",
        "toolVersions": []
    }
    ```
    plus a trailing newline.
  - `round trip`: `decode(encode(sample)) == Ok(sample)`.
  - `same project gives the same text`: encoding `sample` twice and a `sample.copy()` gives identical strings.
  - `a newer schema version is refused`: text with `"schemaVersion": 2` → `TooNew`.
  - `missing schema version is unreadable`, `broken json is unreadable` (`"{not json"`), `wrong types are unreadable` (`"schemaVersion": 1, "name": "x", "vods": "x"`) → `Unreadable` (reason non-blank).
  - `unknown fields are ignored`: valid v1 text with an extra `"future": 1` decodes to `Ok`.
  - `sample`: name `"Hamburg Weekly #12"`; one `Vod("2301234567", "Weekly #12 pt 1", "2026-11-14T13:00:05Z", 7_200_000, "vods/2301234567.mp4", DownloadState.DONE)`; `StartggEvent("https://www.start.gg/tournament/hh-weekly-12/event/sf6", "SF6 Singles", listOf(StartggSet("78901234", MatchDetails("Street Fighter 6", "Winners Final", "Alice", "Bob", "Ryu", "Ken"))))`; one `Match(VodPosition("2301234567", 61_000), VodPosition("2301234567", 754_500), MatchDetails("Street Fighter 6", "Winners Final", "Alice", "Bob", "Ryu", "Ken"), "78901234")`; `descriptionHeader = "Hamburg Weekly #12\nResults: start.gg"`; `exportTitle = "Hamburg Weekly #12 – SF6"`; `toolVersions = listOf(ToolVersion("ffmpeg", "8.1.2"), ToolVersion("yt-dlp", "2026.10.26"))`.
- [ ] Run `make test` → fails (no code).
- [ ] Implement `Project.kt`, `ProjectJson.kt` as in Approach.
- [ ] `make check` passes. Commit `FGC-4: project model and plain-text project.json format`.

### Task 2: Folder names, messages and the in-memory store (core, common)

Files:
- Create `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/project/ProjectNames.kt` (`object ProjectNames { fun folderName(name: String): String? }`).
- Create `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/project/ProjectMessages.kt` (every string in the wording table; `fun saveStatus(state: SaveState)`, `fun cannotOpen(name: String, result: LoadResult): String` — for `Loaded` return `""`).
- Create `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/project/ProjectStore.kt` (`ProjectStore`, `CreateResult`, `LoadResult`).
- Create `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/project/SaveState.kt`.
- Create `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/project/InMemoryProjectStore.kt`.
- Tests: `core/src/commonTest/.../project/ProjectNamesTest.kt`, `ProjectMessagesTest.kt`, `InMemoryProjectStoreTest.kt`.

- [ ] Tests first:
  - `ProjectNamesTest`: `"Hamburg Weekly #12"` → same; `"  Weekly  "` → `"Weekly"`; `"SF6: Top 8 / Finals?"` → `"SF6- Top 8 - Finals-"`; `"a<b>c\"d|e*f\\g"` → `"a-b-c-d-e-f-g"`; `"Weekly..."` → `"Weekly"`; `"tab\there"` → `"tab-here"`; `""`, `"   "`, `"..."` → null; `"con"` → `"con-"`, `"COM1.txt"` → `"COM1.txt-"`, `"Console"` → `"Console"`.
  - `ProjectMessagesTest`: one assertion per row of the wording table (exact text).
  - `InMemoryProjectStoreTest`: create → `Created("/p/Weekly", Project(name = "Weekly"))` for parent `"/p"`, name `" Weekly "`; load it back → `Loaded`; second create same name → `Refused(alreadyExists("Weekly"))`; blank → `Refused(NAME_REQUIRED)`; unknown folder load → `Missing`; `failSaves = true` → `save` throws.
- [ ] Implement. `make check` passes. Commit `FGC-4: project folder names, messages and in-memory store`.

### Task 3: File store with crash-safe saves (core, jvm)

Files:
- Create `core/src/jvmMain/kotlin/de/fgchamburg/vodhelper/core/project/FileProjectStore.kt`.
- Create `core/src/jvmTest/kotlin/de/fgchamburg/vodhelper/core/project/FileProjectStoreTest.kt`.

- [ ] Tests first (temp dirs via `Files.createTempDirectory("project-test")`):
  - `create makes the project folder and file`: create in parent with name `"Hamburg Weekly #12"` → `Created(folder = <parent>/Hamburg Weekly #12 absolute)`; `project.json` content equals `ProjectJson.encode(Project(name = "Hamburg Weekly #12"))`.
  - `create refuses a folder that already has a project`, `create refuses a blank name`, `create refuses a missing parent folder` → the matching `Refused` messages.
  - `create works when the project folder exists but is empty` → `Created`.
  - `save then load returns the same project` (use a project with a VOD and a match).
  - `save leaves no temp files`: after save, folder entries are exactly `["project.json"]`.
  - `a failed write leaves the old file intact`: save project A with the default store; then a store built with `writeTemp = { path, bytes -> Files.write(path, bytes.copyOf(bytes.size / 2)); throw IOException("disk full") }` → `save(B)` throws `IOException`; file text still equals `encode(A)`; no `.tmp` files left.
  - `leftover temp files are ignored and cleaned up`: write `project.json` (A) and a `.project-123.json.tmp` with garbage → `load` returns `Loaded(A)` and the temp file is gone.
  - `missing folder or file loads as missing`.
  - `an unreadable file is never overwritten by open`: write `"{not json"` → `load` is `Unreadable`, file text unchanged.
  - `a newer file loads as too new`.
  - `file is utf-8 plain text`: name `"Kölner Turnier – 東京"` round-trips and the bytes decode as UTF-8 containing the name literally.
- [ ] Implement per Approach (`internal constructor(writeTemp)`, public no-arg constructor; `writeAndSync` private top-level fun using `FileChannel.open(path, WRITE)` + loop write + `force(true)`).
- [ ] `make check` passes. Commit `FGC-4: save projects atomically to project.json`.

### Task 4: Autosave session and recent projects (core)

Files:
- Create `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/project/ProjectSession.kt`.
- Modify `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/settings/AppSettings.kt` (add `recentProjects`, `lastProjectsFolder`, `RecentProject`).
- Create `core/src/commonMain/kotlin/de/fgchamburg/vodhelper/core/settings/RecentProjects.kt`.
- Tests: `core/src/commonTest/.../project/ProjectSessionTest.kt`, `core/src/commonTest/.../settings/RecentProjectsTest.kt`; add to `core/src/jvmTest/.../settings/FileSettingsStoreTest.kt`.

- [ ] Tests first:
  - `ProjectSessionTest` (with `InMemoryProjectStore`): `every change is saved` (update name → store holds the new project, state `Saved`); `an unchanged update does not write` (use a counting `ProjectStore` wrapper: 0 saves); `a failed save is reported and the change is kept` (`failSaves = true` → `Failed("disk full")`, `session.project.name` is the new name, store still has the old); `the next change retries a failed save` (fail, then `failSaves = false`, `update { it }` → `Saved` and store has the latest).
  - `RecentProjectsTest`: `opened puts the project first`; `opening again moves it to the top without duplicates`; `rename updates the name` (same folder, new name → one entry with new name, first); `keeps at most 10` (open 11 → 10, oldest dropped); `removed drops only that folder`.
  - `FileSettingsStoreTest.saves and loads recent projects`: save `AppSettings(true, listOf(RecentProject("/a/Weekly", "Weekly")), "/a")` → load equal. Existing `settings file holds no sign-in details` must still pass unchanged.
- [ ] Implement. `make check` passes. Commit `FGC-4: autosave session and recent projects list`.

### Task 5: Screens and wiring (app)

Files:
- Create `app/src/jvmMain/kotlin/de/fgchamburg/vodhelper/app/FolderPicker.kt` (`FolderPicker` fun interface + `SystemFolderPicker` object).
- Create `app/src/jvmMain/kotlin/de/fgchamburg/vodhelper/app/NewProjectScreen.kt`.
- Create `app/src/jvmMain/kotlin/de/fgchamburg/vodhelper/app/ProjectScreen.kt`.
- Create `app/src/jvmMain/kotlin/de/fgchamburg/vodhelper/app/RecentProjectsSection.kt` (the list used by Home).
- Modify `HomeScreen.kt`, `AppRoot.kt`, `Main.kt`.
- Tests: create `NewProjectScreenTest.kt`, `ProjectScreenTest.kt`; modify `HomeScreenTest.kt`, `AppRootTest.kt`, `UiSnapshots.kt` (all in `app/src/jvmTest/kotlin/de/fgchamburg/vodhelper/app/`).

- [ ] Tests first:
  - `HomeScreenTest`: update existing calls to the new signature (pass `emptyList()` and no-op lambdas); `shows the notice` uses any text now. Add `lists recent projects` (two entries → both names and folders shown, heading "Recent projects"); `no recent projects` → "No projects yet."; `clicking a recent project opens it` (callback gets the entry); `remove calls back`; `recent projects can be opened while an account is missing`.
  - `NewProjectScreenTest`: `create is disabled without a name or folder`; `create is enabled with both and calls back`; `shows the chosen folder and the new folder name` (folder `/Users/x/Videos`, name `"SF6: Top 8"` → shows `/Users/x/Videos` and `The project will be saved in a new folder: SF6- Top 8`); `shows the error`; `choose folder and cancel call back`.
  - `ProjectScreenTest`: `shows the name, folder and saved status`; `typing renames` (`performTextReplacement` → callback gets text); `shows a failed save`; `has no save button` (`onAllNodesWithText("Save")` count 0); `close calls back`.
  - `AppRootTest` (add `projects = InMemoryProjectStore()` and a fake picker returning `"/projects"` to `start()`; settings `setupDone = true` plus Twitch/start.gg connected for project tests):
    - `new project creates and opens the project`: New project → type name `"Weekly"` → Choose folder… → Create project → `"All changes saved"` shown; store has `"/projects/Weekly"`; `settings.current.recentProjects == listOf(RecentProject("/projects/Weekly", "Weekly"))`; `lastProjectsFolder == "/projects"`.
    - `creating an existing project shows why`: store already has `/projects/Weekly` → form shows the `alreadyExists` text.
    - `renaming the project saves it`: after create, replace name text with `"Weekly #13"` → wait until store's project name is `"Weekly #13"` and recent entry name updated.
    - `reopen a recent project`: store has `/p/Old` with `Project(name = "Old")`, settings recent has it → click "Old" → project screen shows "Old" in the name field and "/p/Old".
    - `a missing recent project shows a message`: recent has `/p/Gone` not in store → click → notice `"Gone" can't be opened: its folder or project file is gone.`; entry still listed; "Remove from list" removes it from `settings.current.recentProjects`.
    - `close project returns to the start screen`.
    - Existing tests keep passing (pass the new params).
  - `UiSnapshots`: replace the render list with `01-home-recent-projects.png` (Home, accounts ready, two recent entries `RecentProject("/Users/fgc/Videos/FGC/Hamburg Weekly #12", "Hamburg Weekly #12")`, `RecentProject("/Users/fgc/Videos/FGC/Hamburg Weekly #11", "Hamburg Weekly #11")`), `02-new-project.png` (name `"Hamburg Weekly #13"`, folder `/Users/fgc/Videos/FGC`), `03-project.png` (`Project(name = "Hamburg Weekly #13")`, folder `/Users/fgc/Videos/FGC/Hamburg Weekly #13`, `Saved`), `04-project-save-failed.png` (same with `Failed("The disk is full")`), `05-home-cannot-open.png` (Home with the Missing notice), keep the existing setup/connect/settings renders as `06-…` to `10-…`, and `11-home-no-projects.png`.
- [ ] Implement per Approach. Use `ProjectMessages` for every string. Layout follows `SettingsScreen` (padded `Column`, `OutlinedButton` for Back/Close top-left). Recent list scrolls if long (`verticalScroll` on Home's column or a bounded `LazyColumn`).
- [ ] `make check` passes. Commit `FGC-4: create, open and autosave projects in the app`.

### Task 6: Docs (Haiku)

Files: `CLAUDE.md`.

- [ ] Under "Code layout", after the "Accounts:" bullet, add exactly:
  `- Projects: `core/.../project` (`Project` model, `ProjectJson` for the plain-text `project.json`, `ProjectStore` / `FileProjectStore` with atomic saves, `ProjectSession` for autosave). Any change to the model bumps `PROJECT_SCHEMA_VERSION` only if old files can't be read as-is; add fields with defaults. Recent projects live in `settings.json` (`AppSettings.recentProjects`).`
- [ ] `make check` passes. Commit `FGC-4: document the project store in CLAUDE.md`.

## Out of scope

- Picking VODs, downloads, start.gg linking, match marking, description, export (FGC-8 … FGC-17). They fill the model fields defined here.
- Renaming or moving the project folder on disk (the name in the app can change; the folder keeps its first name).
- "Open project from any folder" (browsing to a `project.json` not in the recent list). Only the recent list reopens projects in this ticket; suggest a follow-up if needed.
- Migrating older schema versions (there is only version 1).
- Flushing the last save when the window is closed mid-write (the file stays valid either way).
