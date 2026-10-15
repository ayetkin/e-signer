package sh.yetkin.esigner.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.SettingsSection
import sh.yetkin.esigner.app.SignFlow
import sh.yetkin.esigner.app.StampStyle
import sh.yetkin.esigner.app.Tab
import sh.yetkin.esigner.app.i18n.LocalStrings
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.FormatMode
import sh.yetkin.esigner.core.PinStatus
import java.time.Instant

/** Workspace layout: the sidebar with navigation, card and signature settings, and [content] on the right. */
@Composable
fun AppShell(state: AppState, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Sidebar(state)
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = LocalTitleBarInset.current), content = content)
    }
}

/**
 * Flat layout, one row per item: navigation on top, signature options under it (sign tab only),
 * and the chosen card plus the settings entry at the bottom, like an account switcher.
 */
@Composable
private fun Sidebar(state: AppState) {
    val busy = state.flow is SignFlow.Signing || state.flow is SignFlow.Confirm
    Column(
        Modifier
            .width(SIDEBAR_WIDTH)
            .fillMaxHeight()
            .background(LocalExtraColors.current.sidebar)
            .padding(top = LocalTitleBarInset.current),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 18.dp)) {
                AppMark(30.dp)
                Text(
                    str("app.name"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            NavItem(AppIcons.DrawOutlined, AppIcons.DrawFilled, str("tab.sign"), state.tab == Tab.SIGN, enabled = !busy) {
                state.selectTab(Tab.SIGN)
            }
            NavItem(AppIcons.VerifiedUserOutlined, AppIcons.VerifiedUserFilled, str("tab.verify"), state.tab == Tab.VERIFY, enabled = !busy) {
                state.selectTab(Tab.VERIFY)
            }

            if (state.tab == Tab.SIGN) {
                Caption(str("sidebar.signature"))
                FormatRow(state)
                StampRow(state)
                val missing = state.settings.missingTsa
                SidebarRow(
                    icon = if (missing) AppIcons.ErrorOutline else AppIcons.Schedule,
                    label = str("settings.level"),
                    value = if (missing) str("level.tsaMissing") else levelSummary(state.settings),
                    valueColor = if (missing) MaterialTheme.colorScheme.error else null,
                    enabled = state.flow == SignFlow.Idle,
                    onClick = { state.openSettings(if (missing) SettingsSection.TIMESTAMP else SettingsSection.SIGNATURE) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Caption(str("sidebar.card"), top = 8.dp)
            CardRow(state)
            NavItem(Icons.Outlined.Settings, Icons.Outlined.Settings, str("settings.title"), selected = false, enabled = state.flow == SignFlow.Idle) {
                state.openSettings()
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, selectedIcon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(animateColorAsState(if (selected) LocalExtraColors.current.selection else Color.Transparent).value)
            .handCursor(enabled && !selected)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (selected) selectedIcon else icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Medium else null)
    }
}

@Composable
private fun Caption(text: String, top: Dp = 24.dp) {
    Text(
        text.uppercase(LocalStrings.current.language.locale),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 10.dp, top = top, bottom = 4.dp),
    )
}

/** An icon, a small label with the current value under it, and a chevron; the whole row is the button. */
@Composable
private fun SidebarRow(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    valueColor: Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .handCursor(enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = valueColor ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (enabled) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Format choice as a row that opens a menu; each option carries its explanation. */
@Composable
private fun FormatRow(state: AppState) =
    ChoiceRow(AppIcons.Description, str("format.label"), listOf(FormatMode.AUTO, FormatMode.CADES_ONLY), state.mode, ::formatKey, state) {
        state.mode = it
    }

/** What signed PDFs show: nothing, a text, an icon, or both. */
@Composable
private fun StampRow(state: AppState) =
    ChoiceRow(AppIcons.DrawOutlined, str("stamp.setting"), StampStyle.entries, state.settings.stamp, { "stamp.${it.name}" }, state) {
        state.settings.updateStamp(it)
    }

/** A row that opens a menu of [options]; [key] gives each option's string key, with "<key>.hint" under it. */
@Composable
private fun <T> ChoiceRow(
    icon: ImageVector,
    label: String,
    options: List<T>,
    selected: T,
    key: (T) -> String,
    state: AppState,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SidebarRow(
            icon = icon,
            label = label,
            value = str(key(selected)),
            enabled = state.flow == SignFlow.Idle,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(320.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(str(key(option)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(str(key(option) + ".hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = {
                        Box(Modifier.size(18.dp)) {
                            if (selected == option) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.handCursor(),
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}

private fun formatKey(mode: FormatMode) = if (mode == FormatMode.AUTO) "format.auto" else "format.cadesOnly"

/**
 * The chosen card as a tile: avatar with a status dot, the holder's name, and one status line (expiry when
 * all is well, otherwise the most serious PIN or certificate problem). Clicking it goes back to card selection.
 */
@Composable
private fun CardRow(state: AppState) {
    val card = state.selectedCard
    val canChange = state.flow !is SignFlow.Signing
    val extra = LocalExtraColors.current
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (card == null) extra.warningContainer else colors.surface)
            .border(1.dp, if (card == null) extra.warning.copy(alpha = 0.3f) else colors.outlineVariant, MaterialTheme.shapes.large)
            .handCursor(canChange)
            .clickable(enabled = canChange, onClick = state::changeCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (card == null) {
            Box(Modifier.size(40.dp).background(extra.warning.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(AppIcons.CreditCard, contentDescription = null, tint = extra.onWarningContainer, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    str(if (state.selectedSlot == null) "card.none" else "card.removed"),
                    style = MaterialTheme.typography.titleSmall,
                    color = extra.onWarningContainer,
                )
                Text(str("card.choose"), style = MaterialTheme.typography.bodySmall, color = colors.primary, fontWeight = FontWeight.Medium)
            }
        } else {
            val holder = card.holder
            val name = holder?.commonName ?: card.label.ifBlank { str("card.unnamed") }
            // Status text, dot color, text color.
            val (status, dot, statusColor) = when {
                card.pinStatus == PinStatus.LOCKED -> Triple(str("pin.locked"), colors.error, colors.error)
                holder?.isValidAt(Instant.now()) == false -> Triple(str("certificate.notValid"), colors.error, colors.error)
                card.pinStatus == PinStatus.FINAL_TRY -> Triple(str("pin.finalTry"), colors.error, colors.error)
                card.pinStatus == PinStatus.COUNT_LOW -> Triple(str("pin.countLow"), extra.warning, extra.warning)
                else -> Triple(holder?.let { str("card.expires", formatDate(it.notAfter)) }, colors.tertiary, colors.onSurfaceVariant)
            }
            Box {
                Avatar(name, 40.dp)
                // Status dot: green ready, amber PIN warning, red blocked or invalid.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .background(colors.surface, CircleShape)
                        .padding(2.dp)
                        .background(dot, CircleShape),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (canChange) {
            Icon(AppIcons.SwapHoriz, contentDescription = str("card.change"), tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

private val SIDEBAR_WIDTH = 272.dp
