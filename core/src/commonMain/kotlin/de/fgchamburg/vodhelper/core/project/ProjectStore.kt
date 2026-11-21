package de.fgchamburg.vodhelper.core.project

sealed interface CreateResult {
    data class Created(val folder: String, val project: Project) : CreateResult
    data class Refused(val message: String) : CreateResult
}

sealed interface LoadResult {
    data class Loaded(val project: Project) : LoadResult
    /** The folder or its project file is gone. */
    data object Missing : LoadResult
    data object TooNew : LoadResult
    data class Unreadable(val reason: String) : LoadResult
}

/** Where projects are kept. Folders are absolute path strings. */
interface ProjectStore {
    fun create(parentFolder: String, name: String): CreateResult
    fun load(folder: String): LoadResult
    /** Throws on failure; the previous file stays as it was. */
    fun save(folder: String, project: Project)
}
