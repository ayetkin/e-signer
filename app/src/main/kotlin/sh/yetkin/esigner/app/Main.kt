package sh.yetkin.esigner.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import sh.yetkin.esigner.app.ui.App
import sh.yetkin.esigner.app.ui.AppTheme
import sh.yetkin.esigner.app.ui.drawAppIcon
import java.awt.Dimension
import java.awt.FileDialog

fun main() = application {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }
    LaunchedEffect(Unit) { state.watchCards() }
    Window(
        onCloseRequest = {
            state.dispose()
            exitApplication()
        },
        title = state.strings["app.name"],
        icon = AppIconPainter,
        state = rememberWindowState(size = DpSize(1040.dp, 720.dp)),
    ) {
        window.minimumSize = Dimension(880, 620)
        AppTheme {
            App(
                state = state,
                pickFiles = {
                    val dialog = FileDialog(window, state.strings["files.chooseTitle"], FileDialog.LOAD).apply {
                        isMultipleMode = true
                        isVisible = true
                    }
                    if (state.tab == Tab.VERIFY) state.addForVerify(dialog.files.toList()) else state.addFiles(dialog.files.toList())
                },
                saveFile = { suggested ->
                    val dialog = FileDialog(window, state.strings["verify.saveTitle"], FileDialog.SAVE).apply {
                        directory = suggested.parent
                        file = suggested.name
                        isVisible = true
                    }
                    dialog.file?.let { java.io.File(dialog.directory, it) }
                },
            )
        }
    }
}

/** Window and taskbar icon; the packaged app uses the .icns/.ico rendered from the same drawing. */
private object AppIconPainter : androidx.compose.ui.graphics.painter.Painter() {
    override val intrinsicSize = androidx.compose.ui.geometry.Size(256f, 256f)

    override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() = drawAppIcon()
}
