package sh.yetkin.esigner.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.Screen
import sh.yetkin.esigner.app.SignFlow
import sh.yetkin.esigner.app.Tab
import sh.yetkin.esigner.app.i18n.LocalStrings

/** Root of the UI; pure function of [state], so it can also be rendered headless for screenshots. */
@Composable
fun App(state: AppState, pickFiles: () -> Unit, saveFile: (java.io.File) -> java.io.File? = { null }) {
    CompositionLocalProvider(LocalStrings provides state.strings) {
        // Surface sets the content color, so plain Text follows the theme (dark mode included).
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    // Initial pass reaches this before the preview, which focuses the stamp again on its own clicks.
                    awaitPointerEventScope {
                        while (true) {
                            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) state.stampFocused = false
                        }
                    }
                },
            ) {
                val flow = state.flow
                // The finished flow itself is the key, so the result screen keeps its results while fading out.
                val page: Any = if (flow is SignFlow.Finished && state.tab == Tab.SIGN) flow else state.screen
                // The window resizes on entering and leaving the card screen, so that switch is instant: a fade would
                // show the old screen squeezed into the new window size.
                if (page == Screen.CARD_SELECT) {
                    Box(Modifier.padding(top = LocalTitleBarInset.current)) { CardSelectScreen(state) }
                } else {
                    Crossfade(page) { page ->
                        if (page is SignFlow.Finished) ResultScreen(state, page.results) else WorkspaceScreen(state, pickFiles, saveFile)
                    }
                }
                AnimatedVisibility(flow is SignFlow.Confirm || flow is SignFlow.Signing, enter = fadeIn(), exit = fadeOut()) {
                    SignDialog(state)
                }
                AnimatedVisibility(state.settingsOpen, enter = fadeIn(), exit = fadeOut()) {
                    SettingsDialog(state)
                }
                AnimatedVisibility(state.aboutOpen, enter = fadeIn(), exit = fadeOut()) {
                    AboutDialog(state)
                }
            }
        }
    }
}
