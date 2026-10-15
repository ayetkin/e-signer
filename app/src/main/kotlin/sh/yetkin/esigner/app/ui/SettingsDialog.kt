package sh.yetkin.esigner.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.UiError
import kotlinx.coroutines.launch
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import sh.yetkin.esigner.app.ScanState
import sh.yetkin.esigner.app.SettingsSection
import sh.yetkin.esigner.app.SignFlow
import sh.yetkin.esigner.app.SigningSettings
import sh.yetkin.esigner.app.StampStyle
import sh.yetkin.esigner.core.VisibleStamp
import kotlin.math.roundToInt
import sh.yetkin.esigner.app.TsaAuthType
import sh.yetkin.esigner.app.Updates
import sh.yetkin.esigner.app.WindowMode
import sh.yetkin.esigner.app.i18n.Language
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.DetectedCard
import sh.yetkin.esigner.core.Pkcs11Library
import sh.yetkin.esigner.core.PinStatus
import sh.yetkin.esigner.core.SigningLevel

/**
 * In-window modal with all settings, grouped into sections. Edits are drafts until "Save"; the card
 * details are read-only and always show the latest scan.
 */
@Composable
fun SettingsDialog(state: AppState) {
    val settings = state.settings
    var language by remember { mutableStateOf(state.language) }
    var windowMode by remember { mutableStateOf(settings.windowMode) }
    var level by remember { mutableStateOf(settings.level) }
    var stamp by remember { mutableStateOf(settings.stamp) }
    var stampHeight by remember { mutableStateOf(settings.stampHeight) }
    var reason by remember { mutableStateOf(state.reason) }
    var location by remember { mutableStateOf(settings.location) }
    var url by remember { mutableStateOf(settings.tsaUrl) }
    var auth by remember { mutableStateOf(settings.authType) }
    var user by remember { mutableStateOf(settings.user) }
    // Only a newly typed password; the kept one is never shown. Handed over as a CharArray on save.
    var password by remember { mutableStateOf("") }
    var clearKept by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf(settings.libraryPath) }
    // The TSA check after saving: running, and its error when the server refused or could not be reached.
    var testing by remember { mutableStateOf(false) }
    var tsaError by remember { mutableStateOf<UiError?>(null) }
    var tsaIncomplete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val kamuSmBlocked = SigningSettings.needsKamuSmAuth(url, auth)

    val close = {
        password = ""
        state.closeSettings()
    }
    // Everything except the timestamp server, which is saved only after it answered.
    val saveOthers = {
        settings.updateStamp(stamp)
        settings.updateStampHeight(stampHeight)
        // Compared with the default before a language change below, the default stays the default.
        settings.updatePdfDetails(reason, location, state.defaultReason)
        settings.updateWindowMode(windowMode)
        if (language != state.language) state.switchLanguage(language)
        if (library.trim() != settings.libraryPath) {
            settings.updateLibraryPath(library)
            state.retryScan()
        }
    }
    val save = save@{
        tsaError = null
        tsaIncomplete = false
        if (kamuSmBlocked) {
            state.settingsSection = SettingsSection.TIMESTAMP
            return@save
        }
        val newPassword = when {
            password.isNotEmpty() -> password.toCharArray()
            clearKept -> CharArray(0)
            else -> null
        }
        val tsaChanged = url.trim() != settings.tsaUrl || auth != settings.authType || user.trim() != settings.user || newPassword != null
        // A timestamp server is saved only once it gave a timestamp with these credentials: when its values change,
        // and when a timestamp level is chosen.
        val check = (tsaChanged && (level.needsTimestamp || url.isNotBlank())) || (level.needsTimestamp && !settings.level.needsTimestamp)
        if (!check) {
            settings.update(level, url, auth, user, newPassword)
            password = ""
            saveOthers()
            state.closeSettings()
            return@save
        }
        val (source, credentials) = settings.timestampFor(url, auth, user, newPassword) ?: run {
            newPassword?.fill('\u0000')
            tsaIncomplete = true
            state.settingsSection = SettingsSection.TIMESTAMP
            return@save
        }
        // The values as they were when Save was clicked; the fields stay editable while the server answers.
        val savedLevel = level
        val savedUrl = url
        val savedAuth = auth
        val savedUser = user
        testing = true
        scope.launch {
            val error = state.testTimestamp(source, credentials)
            testing = false
            if (error == null) {
                settings.update(savedLevel, savedUrl, savedAuth, savedUser, newPassword)
                password = ""
                clearKept = false
                saveOthers()
                state.closeSettings()
            } else {
                newPassword?.fill('\u0000')
                tsaError = error
                state.settingsSection = SettingsSection.TIMESTAMP
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .onPreviewKeyEvent { if (it.key == Key.Escape) { close(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Panel(Modifier.padding(24.dp).widthIn(max = 940.dp).fillMaxWidth().heightIn(max = 720.dp), padding = PaddingValues(0.dp)) {
            Row(Modifier.weight(1f, fill = false).heightIn(min = 420.dp)) {
                Column(
                    Modifier.width(200.dp).fillMaxHeight().background(LocalExtraColors.current.sidebar).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        str("settings.title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 14.dp),
                    )
                    SettingsSection.entries.forEach { section ->
                        SectionItem(sectionIcon(section), str("settings.section.${section.name}"), state.settingsSection == section) {
                            state.settingsSection = section
                        }
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Crossfade(state.settingsSection) { section ->
                        Column {
                            Text(
                                str("settings.section.${section.name}"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(16.dp))
                            when (section) {
                                SettingsSection.GENERAL -> {
                                    Group(str("settings.language")) {
                                        Segmented(Language.entries.map { it to str("language.${it.name}") }, language) { language = it }
                                    }
                                    Group(str("settings.window")) {
                                        OptionList(WindowMode.entries, windowMode, { "settings.window.${it.name}" }) { windowMode = it }
                                    }
                                    // The AGPL asks interactive programs to show the license and the absence of warranty.
                                    Group(str("settings.about")) {
                                        Hint(str("settings.about.license"))
                                        TextButton(onClick = { openLink(Updates.REPOSITORY_URL) }) { Text(str("settings.about.source")) }
                                    }
                                }
                                SettingsSection.SIGNATURE -> {
                                    Group(str("settings.level")) {
                                        OptionList(SigningLevel.entries, level, { "level.${it.name}" }) { level = it }
                                        if (level.needsTimestamp) {
                                            TextButton(onClick = { state.settingsSection = SettingsSection.TIMESTAMP }) {
                                                Text(str("settings.level.tsaLink"))
                                            }
                                        }
                                    }
                                    Group(str("stamp.setting")) {
                                        Segmented(StampStyle.entries.map { it to str("stamp.${it.name}") }, stamp) { stamp = it }
                                        Hint(str("stamp.${stamp.name}.hint"))
                                    }
                                    if (stamp != StampStyle.NONE) {
                                        Group(str("stampSize.setting")) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Slider(
                                                    value = stampHeight,
                                                    onValueChange = { stampHeight = it },
                                                    valueRange = VisibleStamp.MIN_HEIGHT..VisibleStamp.MAX_HEIGHT,
                                                    modifier = Modifier.weight(1f).handCursor(),
                                                )
                                                Text(
                                                    str("stampSize.value", stampHeight.roundToInt()),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(start = 12.dp),
                                                )
                                            }
                                        }
                                    }
                                    Group(str("settings.pdfDetails")) {
                                        OutlinedTextField(
                                            value = reason,
                                            onValueChange = { reason = it },
                                            label = { Text(str("settings.pdfDetails.reason")) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        OutlinedTextField(
                                            value = location,
                                            onValueChange = { location = it },
                                            label = { Text(str("settings.pdfDetails.location")) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Hint(str("settings.pdfDetails.hint"))
                                    }
                                }
                                SettingsSection.TIMESTAMP -> {
                                    if (!level.needsTimestamp) Banner(str("settings.tsa.unused"), Tone.INFO, Modifier.padding(bottom = 16.dp))
                                    tsaError?.let {
                                        Banner(str("settings.tsa.testFailed", errorText(it)), Tone.ERROR, Modifier.padding(bottom = 16.dp), it.detail)
                                    }
                                    if (tsaIncomplete) Banner(str("settings.tsa.incomplete"), Tone.ERROR, Modifier.padding(bottom = 16.dp))
                                    Group(str("settings.tsa.url")) {
                                        OutlinedTextField(
                                            value = url,
                                            onValueChange = { url = it },
                                            placeholder = { Text(SigningSettings.KAMU_SM_URL) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        TextButton(onClick = {
                                            url = SigningSettings.KAMU_SM_URL
                                            auth = TsaAuthType.KAMU_SM
                                        }) { Text(str("settings.tsa.kamuSmPreset")) }
                                    }
                                    Group(str("settings.tsa.auth")) {
                                        Segmented(TsaAuthType.entries.map { it to str("settings.tsa.auth.${it.name}") }, auth) { auth = it }
                                        if (kamuSmBlocked) {
                                            Banner(str("settings.tsa.kamuSmNeedsAuth"), Tone.ERROR, Modifier.padding(top = 8.dp))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            OutlinedTextField(
                                                value = user,
                                                onValueChange = { value ->
                                                    user = if (auth == TsaAuthType.KAMU_SM) value.filter(Char::isDigit) else value
                                                },
                                                label = { Text(str(if (auth == TsaAuthType.KAMU_SM) "settings.tsa.customerNumber" else "settings.tsa.user")) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = if (auth == TsaAuthType.KAMU_SM) KeyboardType.Number else KeyboardType.Text,
                                                ),
                                                modifier = Modifier.weight(1f),
                                            )
                                            OutlinedTextField(
                                                value = password,
                                                onValueChange = { password = it },
                                                // Material shows a placeholder only while focused, so the label says a password is kept.
                                                label = {
                                                    Text(str(if (settings.hasPassword && !clearKept) "settings.tsa.passwordKept" else "settings.tsa.password"))
                                                },
                                                singleLine = true,
                                                visualTransformation = PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Hint(str("settings.tsa.passwordHint") + " " + str("settings.tsa.testHint"), Modifier.weight(1f))
                                            if (settings.hasPassword && !clearKept) {
                                                TextButton(onClick = { clearKept = true; password = "" }) { Text(str("settings.tsa.forgetPassword")) }
                                            }
                                        }
                                    }
                                }
                                SettingsSection.CARD -> {
                                    Group(str("cardSelect.libraryPath")) {
                                        OutlinedTextField(
                                            value = library,
                                            onValueChange = { library = it },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Hint(str("settings.card.libraryHint"), Modifier.weight(1f))
                                            if (library.trim() != Pkcs11Library.defaultPath()) {
                                                TextButton(onClick = { library = Pkcs11Library.defaultPath() }) { Text(str("settings.card.defaultLibrary")) }
                                            }
                                        }
                                    }
                                    Group(str("settings.card.detected")) {
                                        when (val scan = state.scan) {
                                            ScanState.Searching -> Hint(str("cardSelect.searching"))
                                            is ScanState.Failed -> ErrorBanner(scan.error)
                                            is ScanState.Ready -> when {
                                                scan.scan.readerCount == 0 -> Hint(str("cardSelect.noReader.title"))
                                                scan.scan.cards.isEmpty() -> Hint(str("cardSelect.noCard.title"))
                                                else -> scan.scan.cards.forEach { CardDetails(it, it.slotId == state.selectedSlot) }
                                            }
                                        }
                                        TextButton(onClick = state::retryScan) { Text(str("cardSelect.rescan")) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close) { Text(str("common.cancel")) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = save, enabled = !testing && !kamuSmBlocked) {
                    if (testing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Text(str(if (testing) "settings.tsa.testing" else "settings.save"), modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}


private fun sectionIcon(section: SettingsSection): ImageVector = when (section) {
    SettingsSection.GENERAL -> Icons.Outlined.Settings
    SettingsSection.SIGNATURE -> AppIcons.DrawOutlined
    SettingsSection.TIMESTAMP -> AppIcons.Schedule
    SettingsSection.CARD -> AppIcons.CreditCard
}

@Composable
private fun SectionItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(animateColorAsState(if (selected) LocalExtraColors.current.selection else Color.Transparent).value)
            .handCursor(!selected)
            .clickable(enabled = !selected, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Medium else null)
    }
}

/** A labelled block inside a section. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Radio list; [key] gives each option's string key, with "<key>.hint" under it. */
@Composable
private fun <T> OptionList(options: List<T>, selected: T, key: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .selectable(option == selected, role = Role.RadioButton) { onSelect(option) }
                    .handCursor(option != selected)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(selected = option == selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                Column(Modifier.weight(1f)) {
                    Text(str(key(option)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Hint(str(key(option) + ".hint"))
                }
            }
        }
    }
}

/** Everything the scan knows about one card, without a PIN: token, PIN counter and the signing certificate. */
@Composable
private fun CardDetails(card: DetectedCard, selected: Boolean) {
    val holder = card.holder
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(holder?.commonName ?: card.label.ifBlank { str("card.unnamed") }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Detail(str("settings.card.reader"), card.readerName)
        Detail(str("settings.card.model"), card.model)
        Detail(str("settings.card.label"), card.label)
        Detail(str("settings.card.slot"), card.slotId.toString())
        Detail(
            str("settings.card.pin"),
            when (card.pinStatus) {
                PinStatus.OK -> str("settings.card.pinOk")
                PinStatus.COUNT_LOW -> str("pin.countLow")
                PinStatus.FINAL_TRY -> str("pin.finalTry")
                PinStatus.LOCKED -> str("pin.locked")
            },
        )
        if (holder == null) {
            Detail(str("settings.card.certificate"), str("settings.card.noCertificate"))
        } else {
            idLine(holder)?.let { Detail(str("settings.card.id"), it) }
            Detail(str("settings.card.issuer"), holder.issuerName)
            Detail(str("settings.card.validity"), "${formatDate(holder.notBefore)} - ${formatDate(holder.notAfter)}")
            Detail(
                str("settings.card.type"),
                str(
                    when {
                        holder.isQualified5070 -> "settings.card.qualified"
                        holder.isFiscalSeal -> "settings.card.fiscalSeal"
                        else -> "settings.card.otherCertificate"
                    },
                ),
            )
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(150.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/** Gear button that opens the settings; for screens without the sidebar. */
@Composable
fun SettingsButton(state: AppState, modifier: Modifier = Modifier) {
    IconButton(onClick = { state.openSettings() }, modifier = modifier, enabled = state.flow == SignFlow.Idle) {
        Icon(
            Icons.Outlined.Settings,
            contentDescription = str("settings.title"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** One-line summary of the level, e.g. "Timestamped · zd.kamusm.gov.tr". */
@Composable
fun levelSummary(settings: SigningSettings): String =
    if (settings.level.needsTimestamp && settings.tsaUrl.isNotBlank()) {
        str("level.summary", str("level.${settings.level.name}"), settings.tsaHost)
    } else {
        str("level.${settings.level.name}")
    }
