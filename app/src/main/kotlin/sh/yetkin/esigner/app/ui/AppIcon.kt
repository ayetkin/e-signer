package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * The app icon: a sheet with a handwritten signature and a green check seal on a blue tile. Drawn in
 * code so the in-app mark and the packaged icon (rendered by IconTest) are the same artwork.
 * [inset] is the transparent margin as a fraction of the size (macOS icons use about 10%).
 */
@Composable
fun AppIcon(size: Dp, inset: Float = 0f) {
    Canvas(Modifier.size(size)) { drawAppIcon(inset) }
}

fun DrawScope.drawAppIcon(inset: Float = 0f) {
    val full = size.minDimension
    val margin = full * inset
    val s = full - 2 * margin
    fun p(x: Float, y: Float) = Offset(margin + x * s, margin + y * s)

    // Tile
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xFF2B6CB0), Color(0xFF173F6E)),
            start = p(0f, 0f),
            end = p(1f, 1f),
        ),
        topLeft = p(0f, 0f),
        size = Size(s, s),
        cornerRadius = CornerRadius(s * 0.225f),
    )

    // Sheet with a folded corner
    val sheet = Path().apply {
        moveTo(p(0.27f, 0.17f).x, p(0.27f, 0.17f).y)
        lineTo(p(0.60f, 0.17f).x, p(0.60f, 0.17f).y)
        lineTo(p(0.73f, 0.30f).x, p(0.73f, 0.30f).y)
        lineTo(p(0.73f, 0.83f).x, p(0.73f, 0.83f).y)
        lineTo(p(0.27f, 0.83f).x, p(0.27f, 0.83f).y)
        close()
    }
    drawPath(sheet, Color.White)
    val fold = Path().apply {
        moveTo(p(0.60f, 0.17f).x, p(0.60f, 0.17f).y)
        lineTo(p(0.60f, 0.30f).x, p(0.60f, 0.30f).y)
        lineTo(p(0.73f, 0.30f).x, p(0.73f, 0.30f).y)
        close()
    }
    drawPath(fold, Color(0xFFC9D8EA))

    // Text lines
    val line = Color(0xFFB8C7DA)
    listOf(0.33f to 0.52f, 0.41f to 0.64f, 0.49f to 0.58f).forEach { (y, end) ->
        drawLine(line, p(0.34f, y), p(end, y), strokeWidth = s * 0.028f, cap = StrokeCap.Round)
    }

    // Signature
    val signature = Path().apply {
        val a = p(0.33f, 0.68f)
        moveTo(a.x, a.y)
        val c = listOf(
            Triple(p(0.37f, 0.56f), p(0.41f, 0.58f), p(0.40f, 0.66f)),
            Triple(p(0.39f, 0.74f), p(0.45f, 0.70f), p(0.47f, 0.63f)),
            Triple(p(0.49f, 0.58f), p(0.51f, 0.70f), p(0.54f, 0.67f)),
        )
        c.forEach { (c1, c2, e) -> cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y) }
    }
    drawPath(
        signature,
        Color(0xFF173F6E),
        style = Stroke(width = s * 0.03f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawLine(Color(0xFF173F6E), p(0.33f, 0.74f), p(0.56f, 0.74f), strokeWidth = s * 0.012f, cap = StrokeCap.Round)

    // Seal
    val center = p(0.71f, 0.74f)
    drawCircle(Color.White, radius = s * 0.15f, center = center)
    drawCircle(Color(0xFF2F9E6A), radius = s * 0.125f, center = center)
    val check = Path().apply {
        moveTo(center.x - s * 0.06f, center.y + s * 0.002f)
        lineTo(center.x - s * 0.015f, center.y + s * 0.045f)
        lineTo(center.x + s * 0.065f, center.y - s * 0.045f)
    }
    drawPath(check, Color.White, style = Stroke(width = s * 0.032f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
