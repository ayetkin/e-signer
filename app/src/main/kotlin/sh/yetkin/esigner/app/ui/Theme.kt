package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF1D5C8F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F5),
    onPrimaryContainer = Color(0xFF0B2E4B),
    secondaryContainer = Color(0xFFE3ECF4),
    onSecondaryContainer = Color(0xFF1C3347),
    background = Color(0xFFF4F6F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF1F5),
    onSurfaceVariant = Color(0xFF5A6673),
    outline = Color(0xFFB9C3CE),
    outlineVariant = Color(0xFFDDE3EA),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFBE4E2),
    onErrorContainer = Color(0xFF5C1410),
    tertiary = Color(0xFF2E7D4F),
    tertiaryContainer = Color(0xFFDCEFE2),
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
    surfaceVariant = Color(0xFF242A31),
    onSurfaceVariant = Color(0xFFA6B1BD),
    outline = Color(0xFF4D5763),
    outlineVariant = Color(0xFF323941),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C1D19),
    onErrorContainer = Color(0xFFFBE4E2),
    tertiary = Color(0xFF8FD3A8),
    tertiaryContainer = Color(0xFF1D4630),
    onTertiaryContainer = Color(0xFFDCEFE2),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
