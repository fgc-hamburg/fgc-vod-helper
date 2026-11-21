package de.fgchamburg.vodhelper.core.project

/** Whether the latest change to a project has reached the disk. */
sealed interface SaveState {
    data object Saved : SaveState
    data class Failed(val reason: String) : SaveState
}
