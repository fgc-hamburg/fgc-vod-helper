package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.project.ProjectMessages
import de.fgchamburg.vodhelper.core.project.ProjectNames

@Composable
fun NewProjectScreen(
    name: String,
    folder: String?,
    error: String?,
    onNameChange: (String) -> Unit,
    onChooseFolder: () -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    creating: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.align(Alignment.Start)) {
            OutlinedButton(onClick = onCancel) { Text(ProjectMessages.CANCEL) }
        }
        Text(ProjectMessages.NEW_PROJECT_TITLE, style = MaterialTheme.typography.headlineLarge)
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(ProjectMessages.NAME_LABEL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("project-name"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(folder ?: ProjectMessages.NO_FOLDER, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onChooseFolder) { Text(ProjectMessages.CHOOSE_FOLDER) }
            }
            ProjectMessages.willCreate(name)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreate, enabled = !creating && ProjectNames.folderName(name) != null && folder != null) {
                    Text(ProjectMessages.CREATE)
                }
            }
        }
    }
}
