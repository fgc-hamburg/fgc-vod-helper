package de.fgchamburg.vodhelper.core.project

/**
 * An open project. Every change is saved straight away; there is no save button.
 * A change is kept in memory even when saving fails, and the next [update] retries.
 */
class ProjectSession(private val store: ProjectStore, val folder: String, initial: Project) {
    var project: Project = initial
        private set
    var saveState: SaveState = SaveState.Saved
        private set

    /** Applies [change] and saves straight away. Returns the new save state. */
    fun update(change: (Project) -> Project): SaveState {
        val next = change(project)
        if (next == project && saveState is SaveState.Saved) return SaveState.Saved
        project = next
        saveState = try {
            store.save(folder, next)
            SaveState.Saved
        } catch (e: Exception) {
            SaveState.Failed(e.message ?: e::class.simpleName ?: "unknown error")
        }
        return saveState
    }
}
