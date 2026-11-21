package de.fgchamburg.vodhelper.core.settings

import kotlinx.serialization.Serializable

/** Non-secret app settings, kept in settings.json. Sign-in details never go here. */
@Serializable
data class AppSettings(
    val setupDone: Boolean = false,
    val recentProjects: List<RecentProject> = emptyList(),
    val lastProjectsFolder: String? = null,
)

/** A project shown in the start screen's recent list. [folder] is the project folder's absolute path. */
@Serializable
data class RecentProject(val folder: String, val name: String)
