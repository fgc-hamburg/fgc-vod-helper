package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.tools.ToolMessages
import de.fgchamburg.vodhelper.core.tools.ToolStatus
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport

@Composable
fun VideoToolsSection(report: VideoToolsReport?) {
    Column(modifier = Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Video tools", style = MaterialTheme.typography.titleMedium)
        if (report == null) {
            Text("Checking video tools…", style = MaterialTheme.typography.bodyMedium)
        } else {
            for ((tool, status) in report.statuses) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tool.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        ToolMessages.describe(status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status is ToolStatus.Ready) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}
