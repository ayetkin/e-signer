package sh.yetkin.esigner.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowState
import sh.yetkin.esigner.app.ui.App
import sh.yetkin.esigner.app.ui.AppTheme
import sh.yetkin.esigner.app.ui.drawAppIcon
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Taskbar

private val isMac = System.getProperty("os.name").startsWith("Mac")

/** Height of the standard macOS title bar that the content leaves free (traffic lights, window dragging). */
private val MAC_TITLE_BAR_HEIGHT = 28.dp

fun main() {
    if (isMac) {
        // Must be set before AWT starts: title bar follows the system dark mode, menus go to the screen menu bar.
        System.setProperty("apple.awt.application.appearance", "system")
        System.setProperty("apple.laf.useScreenMenuBar", "true")
    }
    runApp()
}

private fun runApp() = application {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }
    val windowState = remember {
        WindowState(
            placement = if (state.settings.windowMode == WindowMode.MAXIMIZED) WindowPlacement.Maximized else WindowPlacement.Floating,
            size = if (state.settings.windowMode == WindowMode.LAST) state.settings.lastWindowSize else SigningSettings.DEFAULT_WINDOW_SIZE,
            position = WindowPosition(Alignment.Center),
        )
    }
    val quit = {
        // For WindowMode.LAST; a maximized window reports the screen size, not the one to restore.
        if (windowState.placement == WindowPlacement.Floating) state.settings.rememberWindowSize(windowState.size)
        state.dispose()
    }
    LaunchedEffect(Unit) {
        // The Compose window icon does not reach the Dock on macOS; the .app bundle has its .icns, run.sh does not.
        // Optional macOS integration: an unsupported feature must not keep card detection from starting.
        if (isMac) runCatching {
            Taskbar.getTaskbar().iconImage =
                AppIconPainter(inset = 0.1f).toAwtImage(Density(1f), LayoutDirection.Ltr, Size(512f, 512f))
        }
        if (isMac) runCatching {
            Desktop.getDesktop().run {
                setPreferencesHandler { state.openSettings() }
                // Cmd+Q would otherwise exit without waiting for a running card step to close its session.
                setQuitHandler { _, response ->
                    quit()
                    response.performQuit()
                }
            }
        }
        state.watchCards()
    }
    Window(
        onCloseRequest = {
            quit()
            exitApplication()
        },
        title = state.strings["app.name"],
        icon = AppIconPainter(),
        state = windowState,
    ) {
        // A changed window setting applies at once, not only on the next start.
        LaunchedEffect(state.settings.windowMode) {
            when (state.settings.windowMode) {
                WindowMode.MAXIMIZED -> windowState.placement = WindowPlacement.Maximized
                WindowMode.DEFAULT -> {
                    windowState.placement = WindowPlacement.Floating
                    windowState.size = SigningSettings.DEFAULT_WINDOW_SIZE
                }
                WindowMode.LAST -> Unit
            }
        }
        window.minimumSize = Dimension(880, 620)
        if (isMac) {
            // Unified title bar: content runs under a transparent title bar, the sidebar reaches the traffic lights.
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            remember { MacGestures.install(window.rootPane) }
        }
        val pickFiles = {
            val dialog = FileDialog(window, state.strings["files.chooseTitle"], FileDialog.LOAD).apply {
                isMultipleMode = true
                isVisible = true
            }
            if (state.tab == Tab.VERIFY) state.addForVerify(dialog.files.toList()) else state.addFiles(dialog.files.toList())
        }
        if (isMac) {
            val inWorkspace = state.screen == Screen.WORKSPACE && state.flow !is SignFlow.Finished
            val switchable = state.flow !is SignFlow.Signing && state.flow !is SignFlow.Confirm
            MenuBar {
                Menu(state.strings["menu.file"]) {
                    Item(
                        state.strings["files.add"] + "\u2026",
                        enabled = inWorkspace && (state.tab == Tab.VERIFY || state.flow == SignFlow.Idle),
                        shortcut = KeyShortcut(Key.O, meta = true),
                        onClick = pickFiles,
                    )
                }
                Menu(state.strings["menu.view"]) {
                    Item(state.strings["tab.sign"], enabled = switchable, shortcut = KeyShortcut(Key.One, meta = true)) {
                        state.selectTab(Tab.SIGN)
                    }
                    Item(state.strings["tab.verify"], enabled = switchable, shortcut = KeyShortcut(Key.Two, meta = true)) {
                        state.selectTab(Tab.VERIFY)
                        state.verifyWithoutCard()
                    }
                }
            }
        }
        AppTheme(titleBarInset = if (isMac) MAC_TITLE_BAR_HEIGHT else 0.dp) {
            App(
                state = state,
                pickFiles = pickFiles,
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

/** Window, taskbar and Dock icon; the packaged app uses the .icns/.ico rendered from the same drawing. */
private class AppIconPainter(private val inset: Float = 0f) : androidx.compose.ui.graphics.painter.Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() = drawAppIcon(inset)
}
