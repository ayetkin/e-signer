package sh.yetkin.esigner.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Path
import org.jetbrains.skia.Surface
import sh.yetkin.esigner.app.ui.AppIcon
import java.io.File
import kotlin.test.Test

/**
 * Renders the app icon to app/build/icon: icon-mac.png (1024 px with the macOS margin, input for
 * iconutil) and icon.png (full bleed, for Windows .ico). The results are copied to app/packaging.
 * Also renders stamp-icon.png and stamp-icon-timestamped.png, the seals drawn in visible signatures (blue, green
 * when the signature gets a timestamp), copied to app/src/main/resources.
 */
class IconTest {
    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(file: File, inset: Float) {
        val scene = ImageComposeScene(width = 1024, height = 1024, density = Density(1f)) { AppIcon(1024.dp, inset) }
        file.writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Test
    fun icon() {
        val out = File("build/icon").apply { mkdirs() }
        render(File(out, "icon-mac.png"), inset = 0.1f)
        render(File(out, "icon.png"), inset = 0f)
    }

    @Test
    fun stampIcon() {
        stampIcon("stamp-icon.png", 0xFF1F6FD1.toInt())
        stampIcon("stamp-icon-timestamped.png", 0xFF2F9E6A.toInt())
    }

    private fun stampIcon(name: String, sealColor: Int) {
        // A rosette seal with a white check mark, on a 48-unit grid scaled to 192 px.
        val surface = Surface.makeRasterN32Premul(192, 192)
        surface.canvas.scale(4f, 4f)
        val seal = "M24 2l4.5 4 6-1 2.4 5.6 5.6 2.4-1 6 4 4.5-4 4.5 1 6-5.6 2.4-2.4 5.6-6-1L24 46l-4.5-4-6 1-2.4-5.6" +
            "L5.5 35l1-6-4-4.5 4-4.5-1-6 5.6-2.4L13.5 5l6 1z"
        surface.canvas.drawPath(Path.makeFromSVGString(seal), Paint().apply { color = sealColor; isAntiAlias = true })
        surface.canvas.drawPath(
            Path.makeFromSVGString("M15 24.5l6 6 12-13"),
            Paint().apply {
                color = 0xFFFFFFFF.toInt()
                isAntiAlias = true
                mode = PaintMode.STROKE
                strokeWidth = 4.5f
                strokeCap = PaintStrokeCap.ROUND
                strokeJoin = PaintStrokeJoin.ROUND
            },
        )
        val out = File("build/icon").apply { mkdirs() }
        File(out, name).writeBytes(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
}
