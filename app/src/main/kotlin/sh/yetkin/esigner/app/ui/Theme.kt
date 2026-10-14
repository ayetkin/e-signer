package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF1D5C8F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9F6),
    onPrimaryContainer = Color(0xFF0B2E4B),
    secondaryContainer = Color(0xFFE6EDF4),
    onSecondaryContainer = Color(0xFF1C3347),
    background = Color(0xFFF3F5F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F4F7),
    onSurface = Color(0xFF16202A),
    onSurfaceVariant = Color(0xFF5B6773),
    outline = Color(0xFFB6C0CB),
    outlineVariant = Color(0xFFE1E6EC),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF5C1410),
    tertiary = Color(0xFF2E7D4F),
    tertiaryContainer = Color(0xFFDDF1E4),
    onTertiaryContainer = Color(0xFF0F3A22),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8CC0EC),
    onPrimary = Color(0xFF07304F),
    primaryContainer = Color(0xFF1C466B),
    onPrimaryContainer = Color(0xFFD6E6F5),
    secondaryContainer = Color(0xFF26313C),
    onSecondaryContainer = Color(0xFFD5E0EA),
    background = Color(0xFF111418),
    surface = Color(0xFF1A1E23),
    surfaceVariant = Color(0xFF22282F),
    onSurface = Color(0xFFE3E8ED),
    onSurfaceVariant = Color(0xFFA6B1BD),
    outline = Color(0xFF4D5763),
    outlineVariant = Color(0xFF2F363E),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C1D19),
    onErrorContainer = Color(0xFFFBE4E2),
    tertiary = Color(0xFF8FD3A8),
    tertiaryContainer = Color(0xFF1D4630),
    onTertiaryContainer = Color(0xFFDCEFE2),
)

/** Colors Material3 has no slot for. */
@Immutable
data class ExtraColors(val warningContainer: Color, val onWarningContainer: Color, val warning: Color)

private val LightExtra = ExtraColors(Color(0xFFFFF3D6), Color(0xFF4F3500), Color(0xFFB07200))
private val DarkExtra = ExtraColors(Color(0xFF3B2E0E), Color(0xFFFFDFA6), Color(0xFFF2C063))

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
            ),
            content = content,
        )
    }
}
