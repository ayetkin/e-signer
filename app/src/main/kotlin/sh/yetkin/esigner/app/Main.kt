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
import sh.yetkin.esigner.app.ui.openLink
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
        // Without it the app menu's About item shows the main class name ("About MainKt").
        System.setProperty("apple.awt.application.name", "E-Signer")
    }
    runApp()
}

private fun runApp() = application {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }
    val windowState = remember { WindowState(size = SigningSettings.CARD_WINDOW_SIZE, position = WindowPosition(Alignment.Center)) }
    val onCardScreen = state.screen == Screen.CARD_SELECT
    val quit = {
        // For WindowMode.LAST; a maximized window reports the screen size, not the one to restore. Reads the screen
        // at call time: the macOS quit handler keeps the first composition's lambda.
        if (state.screen != Screen.CARD_SELECT && windowState.placement == WindowPlacement.Floating) {
            state.settings.rememberWindowSize(windowState.size)
        }
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
                setAboutHandler { state.openAbout() }
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
        // Whether the card screen was shown on the effect's previous run; the app starts on it.
        val wasOnCardScreen = remember { booleanArrayOf(true) }
        // Switching screens resizes the window; a changed window setting applies at once, not only on the next start.
        LaunchedEffect(onCardScreen, state.settings.windowMode) {
            val screenChanged = wasOnCardScreen[0] != onCardScreen
            wasOnCardScreen[0] = onCardScreen
            // Leaving the workspace: keep its size before the window shrinks to the card screen.
            if (screenChanged && onCardScreen && windowState.placement == WindowPlacement.Floating) {
                state.settings.rememberWindowSize(windowState.size)
            }
            window.minimumSize = if (onCardScreen) Dimension(560, 600) else Dimension(880, 620)
            val mode = state.settings.windowMode
            windowState.placement = if (!onCardScreen && mode == WindowMode.MAXIMIZED) WindowPlacement.Maximized else WindowPlacement.Floating
            // A mode change on the card screen, or to LAST in the workspace, keeps the current window.
            if (windowState.placement == WindowPlacement.Floating && (screenChanged || (!onCardScreen && mode != WindowMode.LAST))) {
                windowState.size = when {
                    onCardScreen -> SigningSettings.CARD_WINDOW_SIZE
                    mode == WindowMode.LAST -> state.settings.lastWindowSize
                    else -> SigningSettings.DEFAULT_WINDOW_SIZE
                }
                windowState.position = WindowPosition(Alignment.Center)
            }
        }
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
                Menu(state.strings["menu.window"]) {
                    Item(state.strings["menu.minimize"], shortcut = KeyShortcut(Key.M, meta = true)) {
                        windowState.isMinimized = true
                    }
                    Item(state.strings["menu.zoom"], enabled = !onCardScreen) {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
                    }
                }
                Menu(state.strings["menu.help"]) {
                    Item(state.strings["menu.about"], onClick = { state.openAbout() })
                    Item(state.strings["menu.checkUpdates"], onClick = { state.openAbout(checkUpdates = true) })
                    Separator()
                    Item(state.strings["menu.website"], onClick = { openLink(Updates.REPOSITORY_URL) })
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
