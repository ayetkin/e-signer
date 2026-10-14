package sh.yetkin.esigner.app

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import sh.yetkin.esigner.app.ui.AppTheme
import sh.yetkin.esigner.app.ui.MainScreen
import java.awt.FileDialog
import java.io.File

fun main() = application {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }
    Window(
        onCloseRequest = {
            state.dispose()
            exitApplication()
        },
        title = "E-imza",
        state = rememberWindowState(size = DpSize(1040.dp, 700.dp)),
    ) {
        window.minimumSize = java.awt.Dimension(860, 600)
        AppTheme {
            MainScreen(
                state = state,
                pickFiles = {
                    val dialog = FileDialog(window, "İmzalanacak dosyaları seçin", FileDialog.LOAD).apply {
                        isMultipleMode = true
                        isVisible = true
                    }
                    state.addFiles(dialog.files.toList())
                },
            )
        }
    }
}

internal fun File.sizeLabel(): String {
    val bytes = length()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024))
    }
}
