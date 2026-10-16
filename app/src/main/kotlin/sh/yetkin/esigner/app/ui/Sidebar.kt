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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import sh.yetkin.esigner.app.UpdateCheck
import sh.yetkin.esigner.app.Updates
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
 * and the chosen card at the bottom, like an account switcher. Settings open from the gear next to the app name.
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
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                SettingsButton(state, Modifier.size(32.dp))
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
                SidebarRow(
                    icon = AppIcons.VerifiedUserOutlined,
                    label = str("settings.level"),
                    value = str("level.${state.settings.level.name}"),
                    enabled = state.flow == SignFlow.Idle,
                    onClick = { state.openSettings(SettingsSection.SIGNATURE) },
                )
                TimestampRow(state)
            }
        }
        // Outside the scrolling list, so the main action stays visible in short windows.
        if (state.tab == Tab.SIGN) SignButton(state)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (state.updateCheck as? UpdateCheck.Available)?.let {
                SidebarRow(
                    icon = AppIcons.Download,
                    label = str("sidebar.update"),
                    value = str("about.update.available", it.version),
                    enabled = true,
                    onClick = { openLink(Updates.RELEASES_URL) },
                )
            }
            CardHeader(state)
            CardRow(state)
        }
    }
}

/** Under the signature options; the files are counted in its label. */
@Composable
private fun SignButton(state: AppState) {
    val count = state.files.size
    Button(
        onClick = state::requestSign,
        enabled = state.canSign,
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 14.dp).fillMaxWidth().height(40.dp),
    ) {
        Icon(AppIcons.DrawFilled, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            if (count == 0) str("sign.button") else str("sign.buttonCount", count),
            // Merged so the button's label nudge (see Buttons.kt) still applies.
            style = LocalTextStyle.current.merge(MaterialTheme.typography.titleSmall),
            modifier = Modifier.padding(start = 8.dp),
        )
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
    iconColor: Color? = null,
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
        Icon(icon, contentDescription = null, tint = iconColor ?: valueColor ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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

/**
 * The timestamp server: green when signatures get a timestamp, red when the level needs one that is not set up,
 * "not used" at the standard level. Opens the timestamp settings.
 */
@Composable
private fun TimestampRow(state: AppState) {
    val settings = state.settings
    val (value, color) = when {
        !settings.level.needsTimestamp -> str("sidebar.tsa.unused") to null
        settings.missingTsa -> str("level.tsaMissing") to MaterialTheme.colorScheme.error
        else -> settings.tsaHost to null
    }
    SidebarRow(
        icon = if (color != null) AppIcons.ErrorOutline else AppIcons.Schedule,
        iconColor = if (settings.level.needsTimestamp && color == null) MaterialTheme.colorScheme.tertiary else null,
        label = str("settings.section.TIMESTAMP"),
        value = value,
        valueColor = color,
        enabled = state.flow == SignFlow.Idle,
        onClick = { state.openSettings(SettingsSection.TIMESTAMP) },
    )
}

/** Format choice as a row that opens a menu; each option carries its explanation. */
@Composable
private fun FormatRow(state: AppState) =
    ChoiceRow(
        AppIcons.Description, str("format.label"), listOf(FormatMode.AUTO, FormatMode.CADES_ONLY), state.mode, ::formatKey,
        { if (it == FormatMode.AUTO) AppIcons.PictureAsPdf else AppIcons.Description }, state,
    ) {
        state.mode = it
    }

/** What signed PDFs show: nothing, a text, an icon, or both. */
@Composable
private fun StampRow(state: AppState) =
    ChoiceRow(
        AppIcons.DrawOutlined, str("stamp.setting"), StampStyle.entries, state.settings.stamp, { "stamp.${it.name}" },
        {
            when (it) {
                StampStyle.NONE -> AppIcons.VisibilityOff
                StampStyle.TEXT -> AppIcons.TextFields
                StampStyle.ICON -> AppIcons.Verified
                StampStyle.ICON_TEXT -> AppIcons.DrawOutlined
            }
        },
        state,
    ) {
        state.settings.updateStamp(it)
    }

/**
 * A row that opens a menu of [options]; [key] gives each option's string key, with "<key>.hint" under it, and
 * [optionIcon] the icon in front of it. The chosen option has a check mark at the end.
 */
@Composable
private fun <T> ChoiceRow(
    icon: ImageVector,
    label: String,
    options: List<T>,
    selected: T,
    key: (T) -> String,
    optionIcon: (T) -> ImageVector,
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
                        Icon(optionIcon(option), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
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

/** The section caption with the way back to card selection at its end. */
@Composable
private fun CardHeader(state: AppState) {
    val canChange = state.flow !is SignFlow.Signing
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Caption(str("sidebar.card"), top = 8.dp) }
        if (canChange) {
            val color = MaterialTheme.colorScheme.primary
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .handCursor(true)
                    .clickable(onClick = state::changeCard)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(AppIcons.SwapHoriz, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(
                    str(if (state.selectedCard == null) "card.choose" else "card.change"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = color,
                )
            }
        }
    }
}

/**
 * The chosen card as a tile: the holder and the kind of certificate, then its state (ready, or the most serious
 * PIN or certificate problem) next to the expiry date. Clicking it shows the card's details.
 */
@Composable
private fun CardRow(state: AppState) {
    val card = state.selectedCard
    // A card opens its details in the settings (closed while a signing step runs); without one, card selection.
    val enabled = if (card == null) state.flow !is SignFlow.Signing else state.flow == SignFlow.Idle
    val extra = LocalExtraColors.current
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (card == null) extra.warningContainer else colors.surface)
            .border(1.dp, if (card == null) extra.warning.copy(alpha = 0.3f) else colors.outlineVariant, MaterialTheme.shapes.large)
            .handCursor(enabled)
            .clickable(enabled = enabled) { if (card == null) state.changeCard() else state.openSettings(SettingsSection.CARD) },
    ) {
        if (card == null) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(extra.warning.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.CreditCard, contentDescription = null, tint = extra.onWarningContainer, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        str(if (state.selectedSlot == null) "card.none" else "card.removed"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = extra.onWarningContainer,
                    )
                    Text(str("card.insertHint"), style = MaterialTheme.typography.bodySmall, color = extra.onWarningContainer)
                }
            }
            return@Column
        }
        val holder = card.holder
        val name = holder?.commonName ?: card.label.ifBlank { str("card.unnamed") }
        val kind = when {
            holder?.isQualified5070 == true -> str("card.kind.qualified")
            holder?.isFiscalSeal == true -> str("card.kind.fiscalSeal")
            else -> card.model.ifBlank { card.label }
        }
        // Status text and color: green ready, amber PIN warning, red blocked or invalid.
        val (status, statusColor) = when {
            card.pinStatus == PinStatus.LOCKED -> str("pin.locked") to colors.error
            holder?.isValidAt(Instant.now()) == false -> str("certificate.notValid") to colors.error
            card.pinStatus == PinStatus.FINAL_TRY -> str("pin.finalTry") to colors.error
            card.pinStatus == PinStatus.COUNT_LOW -> str("pin.countLow") to extra.warning
            else -> str("card.ready") to colors.tertiary
        }
        val ready = statusColor == colors.tertiary
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(name, 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (kind.isNotBlank()) {
                    Text(kind, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(statusColor, CircleShape))
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (ready) colors.onSurface else statusColor,
                modifier = Modifier.weight(1f),
            )
            // A warning takes the whole line; the expiry date is shown while all is well.
            if (ready && holder != null) {
                Text(str("card.expires", formatDate(holder.notAfter)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private val SIDEBAR_WIDTH = 272.dp
