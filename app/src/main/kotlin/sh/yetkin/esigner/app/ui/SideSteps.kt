package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.CardState
import sh.yetkin.esigner.app.Phase
import sh.yetkin.esigner.app.UiError
import sh.yetkin.esigner.core.CertificateInfo
import sh.yetkin.esigner.core.FormatMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FormatStep(state: AppState, modifier: Modifier = Modifier) {
    val editable = state.phase == Phase.Editing
    StepCard(2, "İmza biçimi", modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormatOption(
                selected = state.mode == FormatMode.AUTO,
                enabled = editable,
                title = "Otomatik",
                description = "PDF'ler kendi içine imzalanır, diğer dosyalar için .imz oluşturulur.",
                onSelect = { state.mode = FormatMode.AUTO },
            )
            FormatOption(
                selected = state.mode == FormatMode.CADES_ONLY,
                enabled = editable,
                title = "Tümü .imz",
                description = "PDF'ler dahil her dosya için ayrı bir .imz dosyası oluşturulur.",
                onSelect = { state.mode = FormatMode.CADES_ONLY },
            )
        }
    }
}

@Composable
private fun FormatOption(selected: Boolean, enabled: Boolean, title: String, description: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                MaterialTheme.shapes.medium,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CardStep(state: AppState, modifier: Modifier = Modifier) {
    val card = state.card
    StepCard(
        number = 3,
        title = "Kart",
        modifier = modifier,
        trailing = {
            if (card is CardState.Open && state.phase == Phase.Editing) {
                TextButton(onClick = state::lock) { Text("Kartı kapat") }
            }
        },
    ) {
        when (card) {
            CardState.Locked -> PinEntry(state)
            CardState.Opening -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Kart okunuyor...", modifier = Modifier.padding(start = 12.dp))
            }
            is CardState.Open -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                card.session.keys.forEachIndexed { index, key ->
                    CertificateRow(
                        info = key.info,
                        selected = index == card.selectedKey,
                        showSelector = card.session.keys.size > 1,
                        onSelect = { state.selectKey(index) },
                    )
                }
            }
        }
        state.error?.let { ErrorBanner(it, onDismiss = state::dismissError) }
    }
}

@Composable
private fun PinEntry(state: AppState) {
    var pin by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    // The text field needs a String; it is handed over as a CharArray and cleared from the UI state at once.
    val submit = {
        if (pin.isNotEmpty()) {
            state.unlock(pin.toCharArray())
            pin = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 32) pin = it },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = submit, enabled = pin.isNotEmpty(), modifier = Modifier.padding(top = 6.dp)) {
                Text("Kartı aç")
            }
        }
        Hint(Icons.Default.Info, "PIN yalnızca bir kez denenir. Art arda yanlış girişler kartı bloke eder.")
        Row(
            Modifier.clickable { advanced = !advanced }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Gelişmiş", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(
                if (advanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (advanced) {
            OutlinedTextField(
                value = state.libraryPath,
                onValueChange = { state.libraryPath = it },
                label = { Text("PKCS#11 kütüphanesi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.slotIndex.toString(),
                onValueChange = { value -> value.toIntOrNull()?.takeIf { it in 0..15 }?.let { state.slotIndex = it } },
                label = { Text("Okuyucu sırası (0 = ilk)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())

@Composable
private fun CertificateRow(info: CertificateInfo, selected: Boolean, showSelector: Boolean, onSelect: () -> Unit) {
    val valid = info.isValidAt(Instant.now())
    Row(
        Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.medium,
            )
            .selectable(selected = selected, enabled = showSelector, role = Role.RadioButton, onClick = onSelect)
            .padding(12.dp),
    ) {
        if (showSelector) RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(info.commonName, style = MaterialTheme.typography.titleSmall)
            info.maskedNationalId?.let { InfoLine("T.C. Kimlik No", it) }
            InfoLine("Sertifikayı veren", info.issuerName)
            InfoLine("Geçerlilik", "${dateFormat.format(info.notBefore)} - ${dateFormat.format(info.notAfter)}")
            if (!valid) {
                Text(
                    "Sertifika şu an geçerli değil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Hint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBanner(error: UiError, onDismiss: () -> Unit) {
    var showDetail by remember(error) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .padding(start = 12.dp, top = 6.dp, bottom = 10.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(16.dp))
            }
        }
        if (error.detail != null) {
            Text(
                if (showDetail) "Teknik ayrıntıyı gizle" else "Teknik ayrıntı",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.clickable { showDetail = !showDetail }.padding(start = 26.dp),
            )
            if (showDetail) {
                Text(
                    error.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 26.dp, top = 6.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
fun SignStep(state: AppState, modifier: Modifier = Modifier) {
    val count = state.files.size
    StepCard(4, "İmzala", modifier) {
        when (val phase = state.phase) {
            is Phase.Signing -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { phase.done.toFloat() / phase.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${phase.done + 1}/${phase.total} imzalanıyor: ${phase.current}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.confirmed,
                            enabled = count > 0,
                            role = Role.Checkbox,
                            onValueChange = { state.confirmed = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = state.confirmed, onCheckedChange = null, enabled = count > 0)
                    Text(
                        "Listedeki dosyaları inceledim ve imzalamak istiyorum.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                Button(onClick = state::sign, enabled = state.canSign, modifier = Modifier.fillMaxWidth()) {
                    Text(if (count > 0) "$count dosyayı imzala" else "İmzala")
                }
                val missing = when {
                    count == 0 -> "Önce imzalanacak dosyaları ekleyin."
                    state.card !is CardState.Open -> "İmzalamak için PIN ile kartı açın."
                    !state.confirmed -> "Dosyaları gözden geçirip onay kutusunu işaretleyin."
                    else -> null
                }
                missing?.let { Hint(Icons.Default.Info, it) }
            }
        }
    }
}
