package sh.yetkin.esigner.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.yetkin.esigner.app.UiError
import sh.yetkin.esigner.app.i18n.Language
import sh.yetkin.esigner.app.i18n.LocalStrings
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.CertificateInfo
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
fun AppMark(size: Dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.5f))
    }
}

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
    var showDetail by remember(detail) { mutableStateOf(false) }
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
                modifier = Modifier.size(18.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
        }
        if (detail != null) {
            Text(
                str(if (showDetail) "common.hideDetails" else "common.showDetails"),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                modifier = Modifier.padding(start = 28.dp, top = 6.dp).clickable { showDetail = !showDetail },
            )
            if (showDetail) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 28.dp, top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun ErrorBanner(error: UiError, modifier: Modifier = Modifier) {
    Banner(errorText(error), Tone.ERROR, modifier, error.detail)
}

@Composable
fun errorText(error: UiError): String = str("error.${error.error.name}", error.subject.orEmpty())

/** Two-option switch; used for the language and the signature format. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = enabled && !active) { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun LanguageSwitch(current: Language, onSelect: (Language) -> Unit) {
    Segmented(listOf(Language.TR to "TR", Language.EN to "EN"), current, onSelect = onSelect)
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
