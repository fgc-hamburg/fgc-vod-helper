package de.fgchamburg.vodhelper.core.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PROJECT_SCHEMA_VERSION = 1

/** Everything kept in a project's project.json. Property order is the order written to the file. */
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
    /** Twitch video ID. */
    val id: String,
    val title: String,
    /** ISO-8601 UTC as Twitch gives it, e.g. "2026-11-14T13:00:05Z". */
    val broadcastStart: String,
    val lengthMillis: Long,
    /** Relative to the project folder, "/" separators. */
    val localFile: String? = null,
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

/** A point in a VOD, in whole milliseconds so it round-trips exactly. */
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
