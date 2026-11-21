package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NewProjectScreenTest {
    @Test
    fun `create is disabled without a name`() = runComposeUiTest {
        setContent { NewProjectScreen("", "/x", null, {}, {}, {}, {}) }

        onNodeWithText("Create project").assertIsNotEnabled()
    }

    @Test
    fun `create is disabled without a folder`() = runComposeUiTest {
        setContent { NewProjectScreen("Weekly", null, null, {}, {}, {}, {}) }

        onNodeWithText("Create project").assertIsNotEnabled()
    }

    @Test
    fun `create is enabled with both and calls back`() = runComposeUiTest {
        var calls = 0
        setContent { NewProjectScreen("Weekly", "/x", null, {}, {}, { calls++ }, {}) }

        onNodeWithText("Create project").assertIsEnabled().performClick()
        assertEquals(1, calls)
    }

    @Test
    fun `create is disabled while creating`() = runComposeUiTest {
        setContent { NewProjectScreen("Weekly", "/x", null, {}, {}, {}, {}, creating = true) }

        onNodeWithText("Create project").assertIsNotEnabled()
    }

    @Test
    fun `shows the chosen folder and the new folder name`() = runComposeUiTest {
        setContent { NewProjectScreen("SF6: Top 8", "/Users/x/Videos", null, {}, {}, {}, {}) }

        onNodeWithText("/Users/x/Videos").assertExists()
        onNodeWithText("The project will be saved in a new folder: SF6- Top 8").assertExists()
    }

    @Test
    fun `shows no folder chosen`() = runComposeUiTest {
        setContent { NewProjectScreen("", null, null, {}, {}, {}, {}) }

        onNodeWithText("No folder chosen").assertExists()
    }

    @Test
    fun `shows the error`() = runComposeUiTest {
        setContent { NewProjectScreen("Weekly", "/x", "Give the project a name.", {}, {}, {}, {}) }

        onNodeWithText("Give the project a name.").assertExists()
    }

    @Test
    fun `choose folder and cancel call back`() = runComposeUiTest {
        var choose = 0
        var cancel = 0
        setContent { NewProjectScreen("", null, null, {}, { choose++ }, {}, { cancel++ }) }

        onNodeWithText("Choose folder…").performClick()
        onNodeWithText("Cancel").performClick()
        assertEquals(1, choose)
        assertEquals(1, cancel)
    }
}
