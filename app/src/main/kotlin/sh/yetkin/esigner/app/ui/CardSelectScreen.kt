package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.ScanState
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.DetectedCard
import sh.yetkin.esigner.core.PinStatus

@Composable
fun CardSelectScreen(state: AppState) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.align(Alignment.TopEnd).padding(20.dp)) {
            LanguageSwitch(state.language, state::switchLanguage)
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 56.dp)
                .widthIn(max = 540.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppMark(52.dp)
            Spacer(Modifier.height(18.dp))
            Text(str("cardSelect.title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                str("cardSelect.subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Panel(Modifier.fillMaxWidth()) {
                when (val scan = state.scan) {
                    ScanState.Searching -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(str("cardSelect.searching"), modifier = Modifier.padding(start = 12.dp))
                    }
                    is ScanState.Failed -> ErrorBanner(scan.error)
                    is ScanState.Ready -> when {
                        scan.scan.readerCount == 0 ->
                            EmptyState(Icons.Default.Warning, str("cardSelect.noReader.title"), str("cardSelect.noReader.body"))
                        scan.scan.cards.isEmpty() ->
                            EmptyState(Icons.Default.Lock, str("cardSelect.noCard.title"), str("cardSelect.noCard.body"))
                        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            scan.scan.cards.forEach { card ->
                                CardOption(
                                    card = card,
                                    selected = card.slotId == state.selectedSlot,
                                    showRadio = scan.scan.cards.size > 1,
                                    onSelect = { state.selectCard(card.slotId) },
                                )
                            }
                        }
                    }
                }
                if (state.scan !is ScanState.Failed) {
                    Spacer(Modifier.height(16.dp))
                    LiveIndicator(str("cardSelect.autoDetect"))
                }
            }

            Spacer(Modifier.height(20.dp))
            val card = state.selectedCard
            Button(
                onClick = state::continueWithCard,
                enabled = card != null && card.pinStatus != PinStatus.LOCKED,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(str("cardSelect.continue"), style = MaterialTheme.typography.titleSmall)
            }
            if (card?.pinStatus == PinStatus.LOCKED) {
                Spacer(Modifier.height(12.dp))
                Banner(str("pin.lockedHelp"), Tone.ERROR)
            }
            Spacer(Modifier.height(12.dp))
            AdvancedSettings(state)
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CardOption(card: DetectedCard, selected: Boolean, showRadio: Boolean, onSelect: () -> Unit) {
    val holder = card.holder
    val name = holder?.commonName ?: card.label.ifBlank { str("card.unnamed") }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                MaterialTheme.shapes.medium,
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.medium,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(name, 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            holder?.maskedNationalId?.let {
                Text(str("card.nationalId", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (holder != null) {
                Text(
                    holder.issuerName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    str("card.validUntil", formatDate(holder.notAfter)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                card.readerName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.pinStatus != PinStatus.OK || (holder != null && !holder.isValidAt(java.time.Instant.now()))) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PinStatusChip(card.pinStatus)
                    CertificateChips(holder)
                }
            }
        }
        if (showRadio) RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun AdvancedSettings(state: AppState) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { open = !open }) {
            Text(str("cardSelect.advanced"), style = MaterialTheme.typography.labelLarge)
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        if (open) {
            Panel(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.libraryPath,
                    onValueChange = { state.libraryPath = it },
                    label = { Text(str("cardSelect.libraryPath")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = state::retryScan) { Text(str("cardSelect.rescan")) }
                }
            }
        }
    }
}
