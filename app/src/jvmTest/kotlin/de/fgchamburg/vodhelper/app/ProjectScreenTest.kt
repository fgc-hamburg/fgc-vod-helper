package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.project.Project
import de.fgchamburg.vodhelper.core.project.SaveState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProjectScreenTest {
    private val project = Project(name = "Weekly")

    @Test
    fun `shows the name, folder and saved status`() = runComposeUiTest {
        setContent { ProjectScreen(project, "/v/Weekly", SaveState.Saved, {}, {}) }

        onNodeWithTag("project-name").assertExists()
        onNodeWithText("Weekly").assertExists()
        onNodeWithText("/v/Weekly").assertExists()
        onNodeWithText("All changes saved").assertExists()
        onNodeWithText("Picking VODs comes in a later update.").assertExists()
    }

    @Test
    fun `typing renames`() = runComposeUiTest {
        val names = mutableListOf<String>()
        setContent { ProjectScreen(project, "/v/Weekly", SaveState.Saved, { names += it }, {}) }

        onNodeWithTag("project-name").performTextReplacement("Weekly #13")
        assertEquals("Weekly #13", names.last())
    }

    @Test
    fun `shows a failed save`() = runComposeUiTest {
        setContent { ProjectScreen(project, "/v/Weekly", SaveState.Failed("disk full"), {}, {}) }

        onNodeWithText("Couldn't save: disk full. Your changes are kept and will be saved with your next change.")
            .assertExists()
    }

    @Test
    fun `has no save button`() = runComposeUiTest {
        setContent { ProjectScreen(project, "/v/Weekly", SaveState.Saved, {}, {}) }

        onAllNodesWithText("Save").assertCountEquals(0)
    }

    @Test
    fun `close calls back`() = runComposeUiTest {
        var calls = 0
        setContent { ProjectScreen(project, "/v/Weekly", SaveState.Saved, {}, { calls++ }) }

        onNodeWithText("Close project").performClick()
        assertEquals(1, calls)
    }
}
