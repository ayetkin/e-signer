package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

// macOS system colors (systemBlue, label, secondaryLabel, separator, window and control backgrounds).

private val Light = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF00387A),
    secondaryContainer = Color(0xFFE8E8ED),
    onSecondaryContainer = Color(0xFF1D1D1F),
    background = Color(0xFFF5F5F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F4),
    onSurface = Color(0xFF1D1D1F),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE3E3E6),
    error = Color(0xFFD70015),
    errorContainer = Color(0xFFFFE5E7),
    onErrorContainer = Color(0xFF5C0009),
    tertiary = Color(0xFF248A3D),
    tertiaryContainer = Color(0xFFDDF3E3),
    onTertiaryContainer = Color(0xFF0B3A18),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B3A6E),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondaryContainer = Color(0xFF3A3A3C),
    onSecondaryContainer = Color(0xFFF5F5F7),
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF262628),
    surfaceVariant = Color(0xFF2E2E31),
    onSurface = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFF98989D),
    outline = Color(0xFF545458),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
    errorContainer = Color(0xFF4A1512),
    onErrorContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFF30D158),
    tertiaryContainer = Color(0xFF123D1F),
    onTertiaryContainer = Color(0xFFD3F5DD),
)

/** Colors Material3 has no slot for. [sidebar] is the source-list background, [selection] its selected row. */
@Immutable
data class ExtraColors(
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warning: Color,
    val sidebar: Color,
    val selection: Color,
)

private val LightExtra = ExtraColors(Color(0xFFFFF4D6), Color(0xFF4F3500), Color(0xFFC77C02), Color(0xFFEBEBEE), Color(0x14000000))
private val DarkExtra = ExtraColors(Color(0xFF3B2E0E), Color(0xFFFFDFA6), Color(0xFFFFD60A), Color(0xFF242426), Color(0x1FFFFFFF))

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

/** Space the content leaves at the top for the transparent macOS title bar (traffic lights); 0 elsewhere. */
val LocalTitleBarInset = staticCompositionLocalOf { 0.dp }

/**
 * San Francisco on macOS, the platform default elsewhere. ".AppleSystemUIFont" is the name CoreText gives the
 * system UI font; it is used only if Skia can resolve it, so a missing font never breaks text layout.
 */
@OptIn(ExperimentalTextApi::class)
private val systemFontFamily: FontFamily by lazy {
    val mac = System.getProperty("os.name").startsWith("Mac")
    val found = mac && runCatching { FontMgr.default.matchFamilyStyle(MAC_UI_FONT, FontStyle.NORMAL) != null }.getOrDefault(false)
    if (found) {
        FontFamily(listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { SystemFont(MAC_UI_FONT, it) })
    } else {
        FontFamily.Default
    }
}

private const val MAC_UI_FONT = ".AppleSystemUIFont"

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = 0.sp) =
    TextStyle(fontFamily = systemFontFamily, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking)

/** macOS text sizes: 13 pt body, 15-22 pt titles, no Material letter spacing. */
private val MacTypography by lazy {
    Typography(
        headlineSmall = style(22, 28, FontWeight.Bold),
        titleLarge = style(20, 26, FontWeight.SemiBold),
        titleMedium = style(15, 20, FontWeight.SemiBold),
        titleSmall = style(13, 18, FontWeight.SemiBold),
        bodyLarge = style(14, 20),
        bodyMedium = style(13, 18),
        bodySmall = style(12, 16),
        labelLarge = style(13, 18, FontWeight.Medium),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 14, FontWeight.SemiBold, 0.3.sp),
    )
}

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), titleBarInset: Dp = 0.dp, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalExtraColors provides if (dark) DarkExtra else LightExtra,
        LocalTitleBarInset provides titleBarInset,
    ) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = MacTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(10.dp),
            ),
            content = content,
        )
    }
}
