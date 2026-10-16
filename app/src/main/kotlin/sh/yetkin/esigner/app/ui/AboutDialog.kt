package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.UpdateCheck
import sh.yetkin.esigner.app.Updates
import sh.yetkin.esigner.app.i18n.str

private const val DEVELOPER = "Ali Yetkin"
private const val DEVELOPER_URL = "https://github.com/ayetkin"

/**
 * App name, version, developer, license and the GitHub update check (Help menu and the app menu's About item):
 * the icon and name on top, then a list of rows in the sidebar's style.
 */
@Composable
fun AboutDialog(state: AppState) {
    // Takes the focus from the screen underneath, so Escape reaches the dialog and keys do not reach hidden controls.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .onPreviewKeyEvent { if (it.key == Key.Escape) { state.closeAbout(); true } else false }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Panel(Modifier.padding(24.dp).widthIn(max = 480.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AppMark(64.dp)
                Spacer(Modifier.height(12.dp))
                Text(str("app.name"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(str("about.version", Updates.current), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium)) {
                AboutRow(Icons.Outlined.Person, str("about.developer"), DEVELOPER, onClick = { openLink(DEVELOPER_URL) }) {
                    Text(DEVELOPER_URL.removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = colors.primary)
                }
                HorizontalDivider(color = colors.outlineVariant)
                // The AGPL asks interactive programs to show the license and the absence of warranty.
                AboutRow(AppIcons.Description, str("about.license"), str("about.license.name"), note = str("about.license.noWarranty"))
                HorizontalDivider(color = colors.outlineVariant)
                AboutRow(AppIcons.Language, str("settings.about.source"), Updates.REPOSITORY_URL.removePrefix("https://"), onClick = { openLink(Updates.REPOSITORY_URL) })
                HorizontalDivider(color = colors.outlineVariant)
                UpdateRow(state)
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = state::closeAbout) { Text(str("common.close")) }
            }
        }
    }
}

/**
 * An icon, a small label with the value under it, and [trailing] at the end; a row with [onClick] opens a link
 * and ends with the external-link icon.
 */
@Composable
private fun AboutRow(
    icon: ImageVector,
    label: String,
    value: String?,
    note: String? = null,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .handCursor(onClick != null)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor ?: colors.onSurface)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        trailing()
        if (onClick != null) Icon(AppIcons.OpenInNew, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun UpdateRow(state: AppState) {
    val check = state.updateCheck
    val (text, color) = when (check) {
        UpdateCheck.Idle -> null to null
        UpdateCheck.Checking -> str("about.update.checking") to null
        UpdateCheck.UpToDate -> str("about.update.upToDate") to MaterialTheme.colorScheme.tertiary
        is UpdateCheck.Available -> str("about.update.available", check.version) to MaterialTheme.colorScheme.primary
        UpdateCheck.Failed -> str("about.update.failed") to MaterialTheme.colorScheme.error
    }
    // Before any check the row explains when updates are looked up; afterwards it shows the result.
    AboutRow(AppIcons.Download, str("settings.updates"), text, note = if (check == UpdateCheck.Idle) str("about.update.idle") else null, valueColor = color) {
        when (check) {
            UpdateCheck.Checking -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            is UpdateCheck.Available -> Button(onClick = { openLink(Updates.RELEASES_URL) }) { Text(str("about.update.download")) }
            else -> TextButton(onClick = state::checkForUpdates) { Text(str("about.update.check")) }
        }
    }
}
