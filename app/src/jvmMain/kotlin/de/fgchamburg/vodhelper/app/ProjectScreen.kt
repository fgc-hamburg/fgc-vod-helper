package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.project.Project
import de.fgchamburg.vodhelper.core.project.ProjectMessages
import de.fgchamburg.vodhelper.core.project.SaveState

/** The open project. There is no save button: every change is saved straight away. */
@Composable
fun ProjectScreen(
    project: Project,
    folder: String,
    saveState: SaveState,
    onRename: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.align(Alignment.Start)) {
            OutlinedButton(onClick = onClose) { Text(ProjectMessages.CLOSE_PROJECT) }
        }
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = project.name,
                onValueChange = onRename,
                label = { Text(ProjectMessages.NAME_LABEL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("project-name"),
            )
            Text(folder, style = MaterialTheme.typography.bodyMedium)
            Text(ProjectMessages.saveStatus(saveState), style = MaterialTheme.typography.bodyMedium)
            Text(ProjectMessages.NEXT_STEP_PLACEHOLDER, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
