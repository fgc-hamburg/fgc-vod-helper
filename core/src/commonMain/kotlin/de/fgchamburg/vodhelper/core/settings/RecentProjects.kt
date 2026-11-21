package de.fgchamburg.vodhelper.core.settings

object RecentProjects {
    const val MAX = 10

    /** Puts [entry] first, drops any other entry for the same folder, keeps at most [MAX]. */
    fun opened(list: List<RecentProject>, entry: RecentProject): List<RecentProject> =
        (listOf(entry) + list.filter { it.folder != entry.folder }).take(MAX)

    fun removed(list: List<RecentProject>, folder: String): List<RecentProject> =
        list.filter { it.folder != folder }
}
