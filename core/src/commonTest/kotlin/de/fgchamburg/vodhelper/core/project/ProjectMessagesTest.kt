package de.fgchamburg.vodhelper.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectMessagesTest {
    @Test fun `fixed texts`() {
        assertEquals("Recent projects", ProjectMessages.RECENT_HEADING)
        assertEquals("No projects yet.", ProjectMessages.NO_RECENT)
        assertEquals("Remove from list", ProjectMessages.REMOVE_RECENT)
        assertEquals("New project", ProjectMessages.NEW_PROJECT_TITLE)
        assertEquals("Project name", ProjectMessages.NAME_LABEL)
        assertEquals("No folder chosen", ProjectMessages.NO_FOLDER)
        assertEquals("Choose folder…", ProjectMessages.CHOOSE_FOLDER)
        assertEquals("Choose where to keep your projects", ProjectMessages.CHOOSE_FOLDER_TITLE)
        assertEquals("Create project", ProjectMessages.CREATE)
        assertEquals("Cancel", ProjectMessages.CANCEL)
        assertEquals("Give the project a name.", ProjectMessages.NAME_REQUIRED)
        assertEquals("Close project", ProjectMessages.CLOSE_PROJECT)
        assertEquals("Picking VODs comes in a later update.", ProjectMessages.NEXT_STEP_PLACEHOLDER)
    }

    @Test fun `willCreate names the new folder`() {
        assertEquals("The project will be saved in a new folder: SF6- Top 8", ProjectMessages.willCreate("SF6: Top 8"))
        assertNull(ProjectMessages.willCreate("  "))
    }

    @Test fun `creation problems`() {
        assertEquals("The folder /x doesn't exist any more. Choose another one.", ProjectMessages.folderMissing("/x"))
        assertEquals(
            "There is already a project called \"Weekly\" in this folder. Open it from Recent projects or pick another name.",
            ProjectMessages.alreadyExists("Weekly"),
        )
        assertEquals(
            "There is already a folder called \"Weekly\" here with other files in it. Pick another name or folder.",
            ProjectMessages.folderNotEmpty("Weekly"),
        )
        assertEquals("The project couldn't be created: boom", ProjectMessages.couldNotCreate("boom"))
    }

    @Test fun `save status`() {
        assertEquals("All changes saved", ProjectMessages.saveStatus(SaveState.Saved))
        assertEquals(
            "Couldn't save: disk full. Your changes are kept and will be saved with your next change.",
            ProjectMessages.saveStatus(SaveState.Failed("disk full")),
        )
    }

    @Test fun `cannot open`() {
        assertEquals("\"W\" can't be opened: its folder or project file is gone.", ProjectMessages.cannotOpen("W", LoadResult.Missing))
        assertEquals("\"W\" was saved by a newer version of the app. Update the app to open it.", ProjectMessages.cannotOpen("W", LoadResult.TooNew))
        assertEquals(
            "\"W\" can't be opened: its project file is damaged. The file was left as it is.",
            ProjectMessages.cannotOpen("W", LoadResult.Unreadable("x")),
        )
        assertEquals("", ProjectMessages.cannotOpen("W", LoadResult.Loaded(Project(name = "W"))))
    }
}
