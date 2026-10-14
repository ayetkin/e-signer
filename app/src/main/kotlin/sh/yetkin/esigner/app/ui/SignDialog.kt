package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.SignFlow
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.FormatMode
import sh.yetkin.esigner.core.PinStatus
import sh.yetkin.esigner.core.SignatureFormat
import sh.yetkin.esigner.core.formatFor

/** In-window modal: who signs, what is signed, the PIN and an explicit confirmation. */
@Composable
fun SignDialog(state: AppState) {
    val flow = state.flow
    if (flow !is SignFlow.Confirm && flow !is SignFlow.Signing) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Swallows clicks so the screen behind stays inactive.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .onPreviewKeyEvent { if (it.key == Key.Escape) { state.cancelSign(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Panel(
            Modifier.padding(24.dp).width(480.dp).verticalScroll(rememberScrollState()),
            padding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        ) {
            Text(str(if (flow is SignFlow.Signing) "sign.dialog.titleSigning" else "sign.dialog.title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Signer(state)
            Spacer(Modifier.height(12.dp))
            FileSummary(state)
            Spacer(Modifier.height(14.dp))
            when (flow) {
                is SignFlow.Signing -> Progress(flow)
                is SignFlow.Confirm -> PinForm(state, flow)
                else -> Unit
            }
        }
    }
}

@Composable
private fun Signer(state: AppState) {
    val card = state.selectedCard ?: return
    val name = card.holder?.commonName ?: card.label.ifBlank { str("card.unnamed") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(name, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(str("sign.dialog.signer"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            card.holder?.let { idLine(it) }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FileSummary(state: AppState) {
    val files = state.files
    val pades = files.count { formatFor(it, state.mode) == SignatureFormat.PADES }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(str("sign.dialog.files", files.size), style = MaterialTheme.typography.labelLarge)
        files.take(MAX_LISTED).forEach { file ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FormatChip(formatFor(file, state.mode))
            }
        }
        if (files.size > MAX_LISTED) {
            Text(str("sign.dialog.more", files.size - MAX_LISTED), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.mode == FormatMode.AUTO && pades > 0) {
            Text(str("sign.dialog.padesNote"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            str("sign.dialog.level", levelSummary(state.settings)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinForm(state: AppState, flow: SignFlow.Confirm) {
    var pin by remember { mutableStateOf("") }
    var reviewed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(flow.error) { runCatching { focus.requestFocus() } }

    val canSubmit = pin.isNotEmpty() && reviewed && !flow.verifying
    // The field needs a String; it is handed over as a CharArray and cleared from the UI state at once.
    val submit = {
        if (canSubmit) {
            state.sign(pin.toCharArray())
            pin = ""
        }
    }

    // One message at a time: the last-attempt warning wins, then the login error, then the earlier-attempt hint.
    when {
        state.selectedCard?.pinStatus == PinStatus.FINAL_TRY ->
            Banner(str("pin.finalTryHelp"), Tone.ERROR, Modifier.padding(bottom = 12.dp), flow.error?.detail)
        flow.error != null -> ErrorBanner(flow.error, Modifier.padding(bottom = 12.dp))
        state.selectedCard?.pinStatus == PinStatus.COUNT_LOW ->
            Banner(str("pin.countLowHelp"), Tone.WARNING, Modifier.padding(bottom = 12.dp))
    }

    OutlinedTextField(
        value = pin,
        onValueChange = { if (it.length <= 32) pin = it },
        label = { Text(str("sign.dialog.pin")) },
        singleLine = true,
        enabled = !flow.verifying,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        supportingText = { Text(str("sign.dialog.pinHint")) },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = reviewed, enabled = !flow.verifying, role = Role.Checkbox, onValueChange = { reviewed = it }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = reviewed, onCheckedChange = null, enabled = !flow.verifying)
        Text(str("sign.dialog.reviewed"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
    }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = state::cancelSign, enabled = !flow.verifying) { Text(str("common.cancel")) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = submit, enabled = canSubmit, modifier = Modifier.height(44.dp)) {
            if (flow.verifying) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Text(str("sign.dialog.verifying"), modifier = Modifier.padding(start = 10.dp))
            } else {
                Text(str("sign.dialog.confirm"), modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
private fun Progress(flow: SignFlow.Signing) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(progress = { (flow.done + 0.5f) / flow.total }, modifier = Modifier.fillMaxWidth())
        Text(
            str("sign.progress", flow.done + 1, flow.total, flow.current),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(str("sign.progressHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val MAX_LISTED = 3
