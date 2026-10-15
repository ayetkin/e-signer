package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Hand cursor over an enabled clickable element (Compose keeps the arrow by default). */
fun Modifier.handCursor(enabled: Boolean = true): Modifier = if (enabled) pointerHoverIcon(PointerIcon.Hand) else this

// Material3 buttons restyled as macOS push buttons (6 pt corners, 36 pt tall, text buttons 32, compact padding) with the hand
// cursor. They shadow the Material3 names inside this package, so screens call Button/TextButton/OutlinedButton/
// IconButton as usual without importing the Material3 ones. A caller's own height still wins.

private val Padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)

private fun Modifier.macButton(enabled: Boolean, minHeight: Dp = 36.dp) = handCursor(enabled).heightIn(min = minHeight)

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.Button(
        onClick,
        modifier.macButton(enabled),
        enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = Padding,
        content = content,
    )

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.TextButton(
        onClick,
        modifier.macButton(enabled, minHeight = 32.dp),
        enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        content = content,
    )

/** The macOS secondary button: control background, hairline border, label in the text color. */
@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.OutlinedButton(
        onClick,
        modifier.macButton(enabled),
        enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.onSurface),
        border = BorderStroke(1.dp, colors.outline.copy(alpha = if (enabled) 0.8f else 0.4f)),
        contentPadding = Padding,
        content = content,
    )
}

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) =
    androidx.compose.material3.IconButton(onClick, modifier.handCursor(enabled), enabled, content = content)
