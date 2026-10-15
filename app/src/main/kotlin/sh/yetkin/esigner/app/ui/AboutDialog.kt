package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.UpdateCheck
import sh.yetkin.esigner.app.Updates
import sh.yetkin.esigner.app.i18n.str

private const val DEVELOPER = "Ali Yetkin"
private const val DEVELOPER_URL = "https://github.com/ayetkin"

/** App name, version, developer, license and the GitHub update check (Help menu and the app menu's About item). */
@Composable
fun AboutDialog(state: AppState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .onPreviewKeyEvent { if (it.key == Key.Escape) { state.closeAbout(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Panel(Modifier.padding(24.dp).widthIn(max = 440.dp).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AppMark(56.dp)
                Spacer(Modifier.height(12.dp))
                Text(str("app.name"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(str("about.version", Updates.current), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(str("about.developer", DEVELOPER), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { openLink(DEVELOPER_URL) }) { Text(DEVELOPER_URL.removePrefix("https://")) }
                Spacer(Modifier.height(8.dp))
                Text(
                    str("settings.about.license"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { openLink(Updates.REPOSITORY_URL) }) { Text(str("settings.about.source")) }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            UpdateRow(state)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = state::closeAbout) { Text(str("common.close")) }
            }
        }
    }
}

@Composable
private fun UpdateRow(state: AppState) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        val check = state.updateCheck
        val (text, color) = when (check) {
            UpdateCheck.Idle -> str("about.update.idle") to MaterialTheme.colorScheme.onSurfaceVariant
            UpdateCheck.Checking -> str("about.update.checking") to MaterialTheme.colorScheme.onSurfaceVariant
            UpdateCheck.UpToDate -> str("about.update.upToDate") to MaterialTheme.colorScheme.tertiary
            is UpdateCheck.Available -> str("about.update.available", check.version) to MaterialTheme.colorScheme.primary
            UpdateCheck.Failed -> str("about.update.failed") to MaterialTheme.colorScheme.error
        }
        if (check == UpdateCheck.Checking) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.padding(start = 8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
        when (check) {
            is UpdateCheck.Available -> Button(onClick = { openLink(Updates.RELEASES_URL) }) { Text(str("about.update.download")) }
            UpdateCheck.Checking -> Unit
            else -> TextButton(onClick = state::checkForUpdates) { Text(str("about.update.check")) }
        }
    }
}
