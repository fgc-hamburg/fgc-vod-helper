package de.fgchamburg.vodhelper.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectJsonTest {
    private val details = MatchDetails("Street Fighter 6", "Winners Final", "Alice", "Bob", "Ryu", "Ken")

    private val sample = Project(
        name = "Hamburg Weekly #12",
        vods = listOf(
            Vod("2301234567", "Weekly #12 pt 1", "2026-11-14T13:00:05Z", 7_200_000, "vods/2301234567.mp4", DownloadState.DONE),
        ),
        startggEvent = StartggEvent(
            "https://www.start.gg/tournament/hh-weekly-12/event/sf6",
            "SF6 Singles",
            listOf(StartggSet("78901234", details)),
        ),
        matches = listOf(
            Match(VodPosition("2301234567", 61_000), VodPosition("2301234567", 754_500), details, "78901234"),
        ),
        descriptionHeader = "Hamburg Weekly #12\nResults: start.gg",
        exportTitle = "Hamburg Weekly #12 – SF6",
        toolVersions = listOf(ToolVersion("ffmpeg", "8.1.2"), ToolVersion("yt-dlp", "2026.10.26")),
    )

    // Written by hand from the field order in the plan, not generated from the code.
    private val sampleGolden = """
{
    "schemaVersion": 1,
    "name": "Hamburg Weekly #12",
    "vods": [
        {
            "id": "2301234567",
            "title": "Weekly #12 pt 1",
            "broadcastStart": "2026-11-14T13:00:05Z",
            "lengthMillis": 7200000,
            "localFile": "vods/2301234567.mp4",
            "download": "done"
        }
    ],
    "startggEvent": {
        "url": "https://www.start.gg/tournament/hh-weekly-12/event/sf6",
        "name": "SF6 Singles",
        "sets": [
            {
                "id": "78901234",
                "details": {
                    "game": "Street Fighter 6",
                    "round": "Winners Final",
                    "player1": "Alice",
                    "player2": "Bob",
                    "characters1": "Ryu",
                    "characters2": "Ken"
                }
            }
        ]
    },
    "matches": [
        {
            "start": {
                "vodId": "2301234567",
                "millis": 61000
            },
            "end": {
                "vodId": "2301234567",
                "millis": 754500
            },
            "details": {
                "game": "Street Fighter 6",
                "round": "Winners Final",
                "player1": "Alice",
                "player2": "Bob",
                "characters1": "Ryu",
                "characters2": "Ken"
            },
            "startggSetId": "78901234"
        }
    ],
    "descriptionHeader": "Hamburg Weekly #12\nResults: start.gg",
    "exportTitle": "Hamburg Weekly #12 – SF6",
    "toolVersions": [
        {
            "tool": "ffmpeg",
            "version": "8.1.2"
        },
        {
            "tool": "yt-dlp",
            "version": "2026.10.26"
        }
    ]
}
""".trimStart()

    @Test
    fun `writes the exact expected text`() {
        assertEquals(sampleGolden, ProjectJson.encode(sample))
    }

    @Test
    fun `a new project writes every field`() {
        val expected = """
{
    "schemaVersion": 1,
    "name": "Hamburg Weekly #12",
    "vods": [],
    "startggEvent": null,
    "matches": [],
    "descriptionHeader": "",
    "exportTitle": "",
    "toolVersions": []
}
""".trimStart()
        assertEquals(expected, ProjectJson.encode(Project(name = "Hamburg Weekly #12")))
    }

    @Test
    fun `round trip`() {
        assertEquals(ProjectDecodeResult.Ok(sample), ProjectJson.decode(ProjectJson.encode(sample)))
    }

    @Test
    fun `same project gives the same text`() {
        val first = ProjectJson.encode(sample)
        assertEquals(first, ProjectJson.encode(sample))
        assertEquals(first, ProjectJson.encode(sample.copy()))
    }

    @Test
    fun `a newer schema version is refused`() {
        assertEquals(ProjectDecodeResult.TooNew, ProjectJson.decode("""{"schemaVersion": 2, "name": "x"}"""))
    }

    @Test
    fun `missing schema version is unreadable`() {
        assertUnreadable(ProjectJson.decode("""{"name": "x"}"""))
    }

    @Test
    fun `broken json is unreadable`() {
        assertUnreadable(ProjectJson.decode("{not json"))
    }

    @Test
    fun `wrong types are unreadable`() {
        assertUnreadable(ProjectJson.decode("""{"schemaVersion": 1, "name": "x", "vods": "x"}"""))
    }

    @Test
    fun `unknown fields make the file unreadable instead of being dropped on the next save`() =
        assertUnreadable(ProjectJson.decode("""{"schemaVersion": 1, "name": "x", "future": 1}"""))

    private fun assertUnreadable(result: ProjectDecodeResult) {
        assertIs<ProjectDecodeResult.Unreadable>(result)
        assertTrue(result.reason.isNotBlank())
    }
}
