package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.SignFlow
import sh.yetkin.esigner.app.Tab
import sh.yetkin.esigner.app.i18n.LocalStrings
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.FormatMode

/** Workspace layout: the sidebar with navigation, card and signature settings, and [content] on the right. */
@Composable
fun AppShell(state: AppState, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Sidebar(state)
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.weight(1f).fillMaxHeight(), content = content)
    }
}

@Composable
private fun Sidebar(state: AppState) {
    val busy = state.flow is SignFlow.Signing || state.flow is SignFlow.Confirm
    Column(Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                AppMark(32.dp)
                Text(
                    str("app.name"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            NavItem(Icons.Default.Create, str("tab.sign"), state.tab == Tab.SIGN, enabled = !busy) { state.selectTab(Tab.SIGN) }
            NavItem(Icons.Default.Done, str("tab.verify"), state.tab == Tab.VERIFY, enabled = !busy) { state.selectTab(Tab.VERIFY) }

            val card = state.selectedCard
            SectionTitle(str("sidebar.card")) {
                if (card != null) {
                    SmallAction(str("card.change"), enabled = state.flow !is SignFlow.Signing, onClick = state::changeCard)
                }
            }
            CardSection(state)

            if (state.tab == Tab.SIGN) {
                SectionTitle(str("settings.title")) {
                    SmallAction(str("settings.edit"), enabled = state.flow == SignFlow.Idle, onClick = state::openSettings)
                }
                SettingsSection(state)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                str("sidebar.language"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            LanguageSwitch(state.language, state::switchLanguage)
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MaterialTheme.shapes.medium)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (selected) FontWeight.SemiBold else null)
    }
}

@Composable
private fun SectionTitle(text: String, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(LocalStrings.current.language.locale),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

@Composable
private fun SmallAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun CardSection(state: AppState) {
    val card = state.selectedCard
    val canChange = state.flow !is SignFlow.Signing
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (card == null) {
            Chip(str(if (state.selectedSlot == null) "card.none" else "card.removed"), Tone.WARNING)
            OutlinedButton(onClick = state::changeCard, enabled = canChange, modifier = Modifier.fillMaxWidth()) {
                Text(str("card.choose"))
            }
            return@Column
        }
        val holder = card.holder
        val name = holder?.commonName ?: card.label.ifBlank { str("card.unnamed") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(name, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // National id for personal cards, tax id for fiscal seals.
                holder?.let { idLine(it) }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            holder?.let {
                Text(
                    str("card.validUntil", formatDate(it.notAfter)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                card.readerName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (card.pinStatus != sh.yetkin.esigner.core.PinStatus.OK || holder?.isValidAt(java.time.Instant.now()) == false) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PinStatusChip(card.pinStatus)
                CertificateChips(holder)
            }
        }
    }
}

@Composable
private fun SettingsSection(state: AppState) {
    val editable = state.flow == SignFlow.Idle
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(str("format.label"), style = MaterialTheme.typography.labelLarge)
        Segmented(
            listOf(FormatMode.AUTO to str("format.auto"), FormatMode.CADES_ONLY to str("format.cadesOnly")),
            state.mode,
            enabled = editable,
        ) { state.mode = it }
        Text(
            str(if (state.mode == FormatMode.AUTO) "format.auto.hint" else "format.cadesOnly.hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))
        Text(str("settings.level"), style = MaterialTheme.typography.labelLarge)
        val missing = state.settings.missingTsa
        Text(
            if (missing) str("level.tsaMissing") else levelSummary(state.settings),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val SIDEBAR_WIDTH = 280.dp
