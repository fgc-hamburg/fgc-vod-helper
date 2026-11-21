package de.fgchamburg.vodhelper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import de.fgchamburg.vodhelper.core.AppInfo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppScreenTest {
    @Test
    fun `shows name and version`() = runComposeUiTest {
        setContent { AppScreen(AppInfo.from(version = "1.2.3")) }

        onNodeWithText("FGC VOD Helper").assertExists()
        onNodeWithText("Version 1.2.3").assertExists()
    }
}
