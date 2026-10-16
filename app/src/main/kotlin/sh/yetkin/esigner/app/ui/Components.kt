package sh.yetkin.esigner.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.yetkin.esigner.app.UiError
import sh.yetkin.esigner.app.i18n.LocalStrings
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.CertificateInfo
import sh.yetkin.esigner.core.DetectedCard
import sh.yetkin.esigner.core.IdType
import sh.yetkin.esigner.core.PinStatus
import java.awt.Desktop
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.util.Locale

/** White rounded surface with a hairline border, the basic container of the app. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(padding),
        content = content,
    )
}

@Composable
fun AppMark(size: Dp) = AppIcon(size)

@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    val initials = name.split(Regex("\\s+")).filter { it.isNotBlank() }.let { parts ->
        listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull())
    }.joinToString("") { it.take(1).uppercase(Locale.forLanguageTag("tr")) }
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials.ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.36f).sp,
        )
    }
}

enum class Tone { INFO, WARNING, ERROR, SUCCESS }

@Composable
private fun toneColors(tone: Tone): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current
    return when (tone) {
        Tone.INFO -> c.secondaryContainer to c.onSecondaryContainer
        Tone.WARNING -> extra.warningContainer to extra.onWarningContainer
        Tone.ERROR -> c.errorContainer to c.onErrorContainer
        Tone.SUCCESS -> c.tertiaryContainer to c.onTertiaryContainer
    }
}

@Composable
fun Chip(text: String, tone: Tone = Tone.INFO) {
    val (bg, fg) = toneColors(tone)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        modifier = Modifier.background(bg, MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A colored message box; [detail] is shown behind a "technical details" toggle. */
@Composable
fun Banner(text: String, tone: Tone, modifier: Modifier = Modifier, detail: String? = null) {
    val (bg, fg) = toneColors(tone)
    val style = MaterialTheme.typography.bodyMedium
    // Half a capital's height above the baseline, about 0.35 of the font size.
    val capMiddle = with(LocalDensity.current) { (style.fontSize.toPx() * 0.35f).roundToInt() }
    Column(modifier.fillMaxWidth().background(bg, MaterialTheme.shapes.medium).padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                when (tone) {
                    Tone.INFO -> Icons.Default.Info
                    Tone.SUCCESS -> Icons.Default.CheckCircle
                    else -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = fg,
                // Lined up on the text's baseline, not its line box: the icon's middle sits at the middle of the
                // first line's capitals whatever line height the font reports.
                modifier = Modifier.size(18.dp).alignBy { it.measuredHeight / 2 + capMiddle },
            )
            Text(text, style = style, color = fg, modifier = Modifier.weight(1f).alignBy(FirstBaseline))
        }
        if (detail != null) ErrorDetails(detail, fg, Modifier.padding(start = 28.dp, top = 6.dp))
    }
}

/** A "technical details" toggle that opens [detail] in a console-style box with a copy button. */
@Composable
fun ErrorDetails(detail: String, linkColor: Color, modifier: Modifier = Modifier) {
    var open by remember(detail) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(
            str(if (open) "common.hideDetails" else "common.showDetails"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = linkColor,
            modifier = Modifier.handCursor().clickable { open = !open },
        )
        if (open) Console(detail, Modifier.padding(top = 8.dp))
    }
}

/** Terminal look on purpose (dark in both themes): monospace, selectable, scrollable, copyable. */
@Composable
fun Console(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .background(CONSOLE_BG, MaterialTheme.shapes.small)
            .border(1.dp, CONSOLE_BORDER, MaterialTheme.shapes.small),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach {
                Box(Modifier.padding(end = 6.dp).size(9.dp).background(it, CircleShape))
            }
            Spacer(Modifier.weight(1f))
            Text(
                str(if (copied) "common.copied" else "common.copy"),
                style = MaterialTheme.typography.labelMedium,
                color = CONSOLE_DIM,
                modifier = Modifier
                    .handCursor()
                    .clickable {
                        clipboard.setText(AnnotatedString(text))
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        SelectionContainer {
            Text(
                text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = CONSOLE_TEXT,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

private val CONSOLE_BG = Color(0xFF0D1117)
private val CONSOLE_BORDER = Color(0xFF30363D)
private val CONSOLE_TEXT = Color(0xFFC9D1D9)
private val CONSOLE_DIM = Color(0xFF8B949E)

@Composable
fun ErrorBanner(error: UiError, modifier: Modifier = Modifier) {
    Banner(errorText(error), Tone.ERROR, modifier, error.detail)
}

@Composable
fun errorText(error: UiError): String = str("error.${error.error.name}", error.subject.orEmpty())

/** macOS-style segmented switch for a few options. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    // macOS segmented control: a recessed track, the selected segment raised in the control color.
    Row(
        Modifier
            .background(LocalExtraColors.current.selection, MaterialTheme.shapes.small)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .shadow(animateDpAsState(if (active) 1.dp else 0.dp).value, MaterialTheme.shapes.extraSmall)
                    .background(
                        animateColorAsState(if (active) MaterialTheme.colorScheme.surface else Color.Transparent).value,
                        MaterialTheme.shapes.extraSmall,
                    )
                    .handCursor(enabled && !active)
                    .clickable(enabled = enabled && !active) { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }
    }
}

/** A softly pulsing dot next to [text], signalling that detection runs on its own. */
@Composable
fun LiveIndicator(text: String) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).alpha(pulse).background(MaterialTheme.colorScheme.tertiary, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalStrings.current.language.locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)

/**
 * "National ID 12*******01" or "Tax ID 12******90", depending on what the certificate carries; a fiscal seal says
 * so in front ("Fiscal seal, Tax ID 12******90").
 */
@Composable
fun idLine(info: CertificateInfo): String? = info.maskedNationalId?.let {
    val id = str(
        when (info.idType) {
            IdType.NATIONAL_ID -> "card.nationalId"
            IdType.TAX_ID -> "card.taxId"
            IdType.OTHER -> "card.otherId"
        },
        it,
    )
    if (info.isFiscalSeal) str("card.fiscalSealId", id) else id
}

/** The holder's name, or the card's label. */
@Composable
fun cardName(card: DetectedCard): String = card.holder?.commonName ?: card.label.ifBlank { str("card.unnamed") }

/** "Qualified e-signature", "Fiscal seal", or the card model. */
@Composable
fun cardKind(card: DetectedCard): String = when {
    card.holder?.isQualified5070 == true -> str("card.kind.qualified")
    card.holder?.isFiscalSeal == true -> str("card.kind.fiscalSeal")
    else -> card.model.ifBlank { card.label }
}

/** Status text and color: green ready, amber PIN warning, red blocked or invalid; the flag says the card is ready. */
@Composable
fun cardStatus(card: DetectedCard): Triple<String, Color, Boolean> {
    val colors = MaterialTheme.colorScheme
    return when {
        card.pinStatus == PinStatus.LOCKED -> Triple(str("pin.locked"), colors.error, false)
        card.holder?.isValidAt(Instant.now()) == false -> Triple(str("certificate.notValid"), colors.error, false)
        card.pinStatus == PinStatus.FINAL_TRY -> Triple(str("pin.finalTry"), colors.error, false)
        card.pinStatus == PinStatus.COUNT_LOW -> Triple(str("pin.countLow"), LocalExtraColors.current.warning, false)
        else -> Triple(str("card.ready"), colors.tertiary, true)
    }
}

/** A colored dot with the status text next to it. */
@Composable
fun StatusLine(text: String, color: Color, textColor: Color = color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = textColor)
    }
}

@Composable
fun PinStatusChip(status: PinStatus) {
    when (status) {
        PinStatus.OK -> Unit
        PinStatus.COUNT_LOW -> Chip(str("pin.countLow"), Tone.WARNING)
        PinStatus.FINAL_TRY -> Chip(str("pin.finalTry"), Tone.ERROR)
        PinStatus.LOCKED -> Chip(str("pin.locked"), Tone.ERROR)
    }
}

@Composable
fun CertificateChips(info: CertificateInfo?) {
    if (info != null && !info.isValidAt(Instant.now())) Chip(str("certificate.notValid"), Tone.ERROR)
}

@Composable
fun File.sizeLabel(): String {
    val locale = LocalStrings.current.language.locale
    val bytes = length()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(locale, "%.1f KB", bytes / 1024.0)
        else -> String.format(locale, "%.1f MB", bytes / (1024.0 * 1024))
    }
}

fun openFile(file: File) {
    runCatching { Desktop.getDesktop().open(file) }
}

fun revealFile(file: File) {
    runCatching {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(file)
        else desktop.open(file.parentFile)
    }
}

/** Opens [url] in the default browser; nothing happens where the platform cannot. */
fun openLink(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

/** Height of the header rows above the file list and the preview, so the two panels line up side by side. */
val PANEL_HEADER_HEIGHT = 64.dp
