package de.fgchamburg.vodhelper.core.project

/** Plain-language texts about projects, shown to the user. */
object ProjectMessages {
    const val RECENT_HEADING = "Recent projects"
    const val NO_RECENT = "No projects yet."
    const val REMOVE_RECENT = "Remove from list"
    const val NEW_PROJECT_TITLE = "New project"
    const val NAME_LABEL = "Project name"
    const val NO_FOLDER = "No folder chosen"
    const val CHOOSE_FOLDER = "Choose folder…"
    const val CHOOSE_FOLDER_TITLE = "Choose where to keep your projects"
    const val CREATE = "Create project"
    const val CANCEL = "Cancel"
    const val NAME_REQUIRED = "Give the project a name."
    const val CLOSE_PROJECT = "Close project"
    const val NEXT_STEP_PLACEHOLDER = "Picking VODs comes in a later update."

    /** Names the new folder only, so the wording is the same on every OS. Null when the name gives no folder. */
    fun willCreate(name: String): String? =
        ProjectNames.folderName(name)?.let { "The project will be saved in a new folder: $it" }

    fun folderMissing(folder: String): String = "The folder $folder doesn't exist any more. Choose another one."

    fun alreadyExists(folderName: String): String =
        "There is already a project called \"$folderName\" in this folder. Open it from Recent projects or pick another name."

    fun folderNotEmpty(folderName: String): String =
        "There is already a folder called \"$folderName\" here with other files in it. Pick another name or folder."

    fun couldNotCreate(reason: String): String = "The project couldn't be created: $reason"

    fun saveStatus(state: SaveState): String = when (state) {
        SaveState.Saved -> "All changes saved"
        is SaveState.Failed -> "Couldn't save: ${state.reason}. Your changes are kept and will be saved with your next change."
    }

    fun cannotOpen(name: String, result: LoadResult): String = when (result) {
        is LoadResult.Loaded -> ""
        LoadResult.Missing -> "\"$name\" can't be opened: its folder or project file is gone."
        LoadResult.TooNew -> "\"$name\" was saved by a newer version of the app. Update the app to open it."
        is LoadResult.Unreadable -> "\"$name\" can't be opened: its project file is damaged. The file was left as it is."
    }
}
