package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.project.ProjectMessages
import de.fgchamburg.vodhelper.core.settings.RecentProject

/** The "Recent projects" list on the start screen. */
@Composable
fun RecentProjectsSection(
    recent: List<RecentProject>,
    onOpen: (RecentProject) -> Unit,
    onRemove: (RecentProject) -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ProjectMessages.RECENT_HEADING, style = MaterialTheme.typography.titleMedium)
        if (recent.isEmpty()) {
            Text(ProjectMessages.NO_RECENT, style = MaterialTheme.typography.bodyMedium)
        }
        for (entry in recent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { onOpen(entry) }) { Text(entry.name) }
                    Text(entry.folder, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onRemove(entry) }) { Text(ProjectMessages.REMOVE_RECENT) }
            }
        }
    }
}
