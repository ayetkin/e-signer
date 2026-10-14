package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.SigningSettings
import sh.yetkin.esigner.app.TsaAuthType
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.SigningLevel

/** In-window modal for the signature level and the time-stamping authority. */
@Composable
fun SettingsDialog(state: AppState) {
    if (!state.settingsOpen) return
    val settings = state.settings
    var level by remember { mutableStateOf(settings.level) }
    var url by remember { mutableStateOf(settings.tsaUrl) }
    var auth by remember { mutableStateOf(settings.authType) }
    var user by remember { mutableStateOf(settings.user) }
    // Only a newly typed password; the kept one is never shown. Handed over as a CharArray on save.
    var password by remember { mutableStateOf("") }
    var clearKept by remember { mutableStateOf(false) }

    val close = {
        password = ""
        state.closeSettings()
    }
    val save = {
        val newPassword = when {
            password.isNotEmpty() -> password.toCharArray()
            clearKept -> CharArray(0)
            else -> null
        }
        settings.update(level, url, auth, user, newPassword)
        password = ""
        state.closeSettings()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .onPreviewKeyEvent { if (it.key == Key.Escape) { close(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Panel(
            Modifier.padding(24.dp).width(520.dp).verticalScroll(rememberScrollState()),
            padding = PaddingValues(24.dp),
        ) {
            Text(str("settings.title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            Text(str("settings.level"), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Segmented(
                SigningLevel.entries.map { it to str("level.${it.name}") },
                level,
            ) { level = it }
            Spacer(Modifier.height(8.dp))
            Text(
                str("level.${level.name}.hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (level.needsTimestamp) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(str("settings.tsa"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        url = SigningSettings.KAMU_SM_URL
                        auth = TsaAuthType.KAMU_SM
                    }) { Text(str("settings.tsa.kamuSmPreset")) }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(str("settings.tsa.url")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(str("settings.tsa.auth"), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Segmented(TsaAuthType.entries.map { it to str("settings.tsa.auth.${it.name}") }, auth) { auth = it }
                if (auth != TsaAuthType.NONE) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = user,
                            onValueChange = { value ->
                                user = if (auth == TsaAuthType.KAMU_SM) value.filter(Char::isDigit) else value
                            },
                            label = { Text(str(if (auth == TsaAuthType.KAMU_SM) "settings.tsa.customerNumber" else "settings.tsa.user")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (auth == TsaAuthType.KAMU_SM) KeyboardType.Number else KeyboardType.Text,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            // Material shows a placeholder only while focused, so the label says a password is kept.
                            label = {
                                Text(str(if (settings.hasPassword && !clearKept) "settings.tsa.passwordKept" else "settings.tsa.password"))
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            str("settings.tsa.passwordHint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(top = 6.dp),
                        )
                        if (settings.hasPassword && !clearKept) {
                            TextButton(onClick = { clearKept = true; password = "" }) { Text(str("settings.tsa.forgetPassword")) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close) { Text(str("common.cancel")) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = save) { Text(str("settings.save"), modifier = Modifier.padding(horizontal = 8.dp)) }
            }
        }
    }
}

/** One-line summary of the level, e.g. "Timestamped · zd.kamusm.gov.tr". */
@Composable
fun levelSummary(settings: SigningSettings): String =
    if (settings.level.needsTimestamp && settings.tsaUrl.isNotBlank()) {
        str("level.summary", str("level.${settings.level.name}"), settings.tsaHost)
    } else {
        str("level.${settings.level.name}")
    }
