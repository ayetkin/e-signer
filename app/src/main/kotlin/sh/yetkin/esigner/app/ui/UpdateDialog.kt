package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

/**
 * The update window, opened from the sidebar's update row and from About: the version step on top, then the
 * download with its progress, and the downloaded package with the button that starts its installer.
 */
@Composable
fun UpdateDialog(state: AppState) {
    val check = state.updateCheck
    val version = when (check) {
        is UpdateCheck.Available -> check.version
        is UpdateCheck.Downloading -> check.version
        is UpdateCheck.Downloaded -> check.version
        else -> return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .onPreviewKeyEvent { if (it.key == Key.Escape) { state.closeUpdate(); true } else false }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Panel(Modifier.padding(24.dp).widthIn(max = 460.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppMark(56.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    str(
                        when (check) {
                            is UpdateCheck.Downloading -> "update.title.downloading"
                            is UpdateCheck.Downloaded -> "update.title.ready"
                            else -> "update.title"
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(Updates.current, Tone.INFO)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Chip(version, Tone.SUCCESS)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                when (check) {
                    is UpdateCheck.Available -> {
                        if (check.downloadFailed) Banner(str("update.failed"), Tone.ERROR)
                        else Text(
                            str(if (check.asset == null) "update.manual" else "update.howItWorks"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    is UpdateCheck.Downloading -> DownloadProgress(check)
                    is UpdateCheck.Downloaded -> {
                        Banner(str("update.verified", check.file.name), Tone.SUCCESS)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            str(if (isMac) "update.installNote.mac" else "update.installNote"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    else -> {}
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Once downloaded, the row has room only for the install actions.
                if (check !is UpdateCheck.Downloaded) {
                    TextButton(onClick = { openLink(Updates.RELEASES_URL) }) { Text(str("about.update.notes")) }
                }
                Spacer(Modifier.weight(1f))
                // Closing leaves a running download going; the sidebar row keeps its progress.
                TextButton(onClick = state::closeUpdate) { Text(str("common.close")) }
                when (check) {
                    is UpdateCheck.Available ->
                        if (check.asset == null) Button(onClick = { openLink(Updates.RELEASES_URL) }) { Text(str("update.releasesPage")) }
                        else Button(onClick = state::downloadUpdate) {
                            Text(str(if (check.downloadFailed) "update.retry" else "about.update.download"))
                        }
                    is UpdateCheck.Downloading -> OutlinedButton(onClick = state::cancelUpdateDownload) { Text(str("update.stop")) }
                    is UpdateCheck.Downloaded -> {
                        TextButton(onClick = { revealFile(check.file) }) { Text(str("result.showFolder")) }
                        Button(onClick = { openFile(check.file) }) { Text(str("update.install")) }
                    }
                    else -> {}
                }
            }
        }
    }
}

/** A determinate bar with "12.3 MB / 98.1 MB" and the percentage; an indeterminate one when the size is unknown. */
@Composable
private fun DownloadProgress(check: UpdateCheck.Downloading) {
    val colors = MaterialTheme.colorScheme
    val fraction = if (check.total > 0) (check.received.toFloat() / check.total).coerceIn(0f, 1f) else null
    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        val received = bytesLabel(check.received)
        Text(
            if (check.total > 0) "$received / ${bytesLabel(check.total)}" else received,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (fraction != null) Text(str("update.percent", (fraction * 100).toInt()), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
