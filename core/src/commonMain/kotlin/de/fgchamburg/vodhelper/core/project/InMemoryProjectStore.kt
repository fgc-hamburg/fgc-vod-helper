package de.fgchamburg.vodhelper.core.project

/** A [ProjectStore] kept in memory, for tests. */
class InMemoryProjectStore : ProjectStore {
    val projects: MutableMap<String, Project> = mutableMapOf()
    var failSaves = false

    override fun create(parentFolder: String, name: String): CreateResult {
        val folderName = ProjectNames.folderName(name) ?: return CreateResult.Refused(ProjectMessages.NAME_REQUIRED)
        val folder = "$parentFolder/$folderName"
        if (folder in projects) return CreateResult.Refused(ProjectMessages.alreadyExists(folderName))
        val project = Project(name = name.trim())
        projects[folder] = project
        return CreateResult.Created(folder, project)
    }

    override fun load(folder: String): LoadResult =
        projects[folder]?.let { LoadResult.Loaded(it) } ?: LoadResult.Missing

    override fun save(folder: String, project: Project) {
        if (failSaves) throw IllegalStateException("disk full")
        projects[folder] = project
    }
}
