package de.fgchamburg.vodhelper.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import de.fgchamburg.vodhelper.core.AppInfo
import de.fgchamburg.vodhelper.core.tools.VideoToolsReport

@Composable
fun AppScreen(info: AppInfo, tools: VideoToolsReport?) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(info.name, style = MaterialTheme.typography.headlineLarge)
                Text(info.versionLabel, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(32.dp))
                VideoToolsSection(tools)
            }
        }
    }
}
