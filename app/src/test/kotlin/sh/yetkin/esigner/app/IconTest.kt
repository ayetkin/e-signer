package sh.yetkin.esigner.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import sh.yetkin.esigner.app.ui.AppIcon
import java.io.File
import kotlin.test.Test

/**
 * Renders the app icon to app/build/icon: icon-mac.png (1024 px with the macOS margin, input for
 * iconutil) and icon.png (full bleed, for Windows .ico). The results are copied to app/packaging.
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
}
