package de.fgchamburg.vodhelper.core.tools

/** Status of every tool, always in [VideoTool] order. */
class VideoToolsReport(statuses: Map<VideoTool, ToolStatus>) {
    init {
        require(statuses.keys == VideoTool.entries.toSet()) { "Report must cover every tool" }
    }

    val statuses: Map<VideoTool, ToolStatus> = VideoTool.entries.associateWith { statuses.getValue(it) }

    val allReady: Boolean get() = statuses.values.all { it is ToolStatus.Ready }

    /** Version of a ready tool; throws [VideoToolUnavailableException] otherwise. Steps call this before using a tool. */
    fun require(tool: VideoTool): String =
        when (val status = statuses.getValue(tool)) {
            is ToolStatus.Ready -> status.version
            else -> throw VideoToolUnavailableException(tool, status)
        }

    companion object {
        fun allMissing(path: String) =
            VideoToolsReport(VideoTool.entries.associateWith { ToolStatus.Missing(path) })
    }
}

class VideoToolUnavailableException(val tool: VideoTool, val status: ToolStatus) :
    IllegalStateException(ToolMessages.stepBlocked(tool, status))
