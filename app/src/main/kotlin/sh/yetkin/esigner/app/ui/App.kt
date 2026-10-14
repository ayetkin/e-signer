package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.Screen
import sh.yetkin.esigner.app.SignFlow
import sh.yetkin.esigner.app.i18n.LocalStrings

/** Root of the UI; pure function of [state], so it can also be rendered headless for screenshots. */
@Composable
fun App(state: AppState, pickFiles: () -> Unit) {
    CompositionLocalProvider(LocalStrings provides state.strings) {
        // Surface sets the content color, so plain Text follows the theme (dark mode included).
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                val flow = state.flow
                when {
                    state.screen == Screen.CARD_SELECT -> CardSelectScreen(state)
                    flow is SignFlow.Finished -> ResultScreen(state, flow.results)
                    else -> {
                        WorkspaceScreen(state, pickFiles)
                        SignDialog(state)
                    }
                }
            }
        }
    }
}
