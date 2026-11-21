package de.fgchamburg.vodhelper.core.tools

/** Reads tool versions out of what the tools print. Returns null when the output isn't recognised. */
object ToolVersionParser {
    private val ffVersion = Regex("""^(ffmpeg|ffprobe) version (\S+)""")
    private val ytDlpVersion = Regex("""^\d{4}\.\d{2}\.\d{2}(\.\d+)?$""")
    private val vlcVersion = Regex("""^\d+\.\d+\.\d+(\.\d+)?$""")

    fun ffmpeg(output: String): String? = ff("ffmpeg", output)

    fun ffprobe(output: String): String? = ff("ffprobe", output)

    fun ytDlp(output: String): String? =
        firstLine(output)?.takeIf { ytDlpVersion.matches(it) }

    fun vlc(raw: String): String? =
        raw.trim().substringBefore(' ').takeIf { vlcVersion.matches(it) }

    private fun ff(program: String, output: String): String? {
        val match = firstLine(output)?.let { ffVersion.find(it) } ?: return null
        if (match.groupValues[1] != program) return null
        return match.groupValues[2].takeIf { it.first().isDigit() }
    }

    private fun firstLine(output: String): String? =
        output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
}
