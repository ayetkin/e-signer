package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.SignFlow
import sh.yetkin.esigner.app.Tab
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.FormatMode
import sh.yetkin.esigner.core.SignatureFormat
import sh.yetkin.esigner.core.formatFor
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DropTargetDropEvent
import java.io.File

@Composable
fun WorkspaceScreen(state: AppState, pickFiles: () -> Unit, saveFile: (File) -> File?) {
    var hovering by remember { mutableStateOf(false) }
    val verify = state.tab == Tab.VERIFY
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(state)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .fileDropTarget(
                    onHover = { hovering = it },
                    onFiles = { if (state.tab == Tab.VERIFY) state.addForVerify(it) else state.addFiles(it) },
                )
                .padding(24.dp),
        ) {
            when {
                verify -> VerifyPane(state, hovering, pickFiles, saveFile, Modifier.fillMaxSize())
                state.files.isEmpty() -> DropZone(
                    hovering = hovering,
                    title = str(if (hovering) "files.dropNow" else "files.dropTitle"),
                    body = str("files.dropBody"),
                    pickFiles = pickFiles,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> FileList(state, hovering, pickFiles, Modifier.fillMaxSize())
            }
        }
        if (!verify) BottomBar(state)
    }
}

@Composable
fun TopBar(state: AppState) {
    Column {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppMark(32.dp)
            Text(
                str("app.name"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, end = 28.dp),
            )
            Segmented(
                listOf(Tab.SIGN to str("tab.sign"), Tab.VERIFY to str("tab.verify")),
                state.tab,
                enabled = state.flow !is SignFlow.Signing && state.flow !is SignFlow.Confirm,
                onSelect = state::selectTab,
            )
            Spacer(Modifier.weight(1f))
            LanguageSwitch(state.language, state::switchLanguage)
            Spacer(Modifier.width(16.dp))
            CardChip(state)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CardChip(state: AppState) {
    val card = state.selectedCard
    Row(
        Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (card == null) {
            Chip(str(if (state.selectedSlot == null) "card.none" else "card.removed"), Tone.WARNING)
        } else {
            val name = card.holder?.commonName ?: card.label.ifBlank { str("card.unnamed") }
            Avatar(name, 30.dp)
            Column {
                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                card.holder?.let { idLine(it) }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = state::changeCard, enabled = state.flow !is SignFlow.Signing) {
            Text(str(if (state.selectedSlot == null) "card.choose" else "card.change"))
        }
    }
}

@Composable
fun DropZone(hovering: Boolean, title: String, body: String, pickFiles: () -> Unit, modifier: Modifier) {
    val accent = if (hovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier
            .background(
                if (hovering) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.large,
            )
            .dashedBorder(accent),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = pickFiles) { Text(str("files.choose")) }
        }
    }
}

@Composable
private fun FileList(state: AppState, hovering: Boolean, pickFiles: () -> Unit, modifier: Modifier) {
    val editable = state.flow == SignFlow.Idle
    Panel(
        modifier.then(
            if (hovering) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier,
        ),
        padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(str("files.count", state.files.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    str(if (hovering) "files.dropNow" else "files.reviewHint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editable) {
                TextButton(onClick = state::clearFiles) { Text(str("files.clear")) }
                OutlinedButton(onClick = pickFiles) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(str("files.add"), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(state.files, key = { it.path }) { file ->
                FileRow(file, formatFor(file, state.mode), removable = editable, onRemove = { state.removeFile(file) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 76.dp))
            }
        }
    }
}

@Composable
private fun FileRow(file: File, format: SignatureFormat, removable: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExtensionBadge(file.extension)
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${file.sizeLabel()}  ·  ${file.parent}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FormatChip(format)
        TextButton(onClick = { openFile(file) }) { Text(str("files.view")) }
        if (removable) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = str("files.remove"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun ExtensionBadge(extension: String) {
    val pdf = extension.equals("pdf", ignoreCase = true)
    Box(
        Modifier
            .size(width = 42.dp, height = 48.dp)
            .background(
                if (pdf) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            extension.uppercase().take(4).ifEmpty { "?" },
            color = if (pdf) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

@Composable
fun FormatChip(format: SignatureFormat) {
    Chip(str(if (format == SignatureFormat.PADES) "format.chip.pades" else "format.chip.cades"))
}

@Composable
private fun BottomBar(state: AppState) {
    val editable = state.flow == SignFlow.Idle
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(str("format.label"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Segmented(
                listOf(FormatMode.AUTO to str("format.auto"), FormatMode.CADES_ONLY to str("format.cadesOnly")),
                state.mode,
                enabled = editable,
            ) { state.mode = it }
            Text(
                str(if (state.mode == FormatMode.AUTO) "format.auto.hint" else "format.cadesOnly.hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            val count = state.files.size
            Button(
                onClick = state::requestSign,
                enabled = state.canSign,
                modifier = Modifier.height(44.dp),
            ) {
                Text(
                    if (count == 0) str("sign.button") else str("sign.buttonCount", count),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(16.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.fileDropTarget(onHover: (Boolean) -> Unit, onFiles: (List<File>) -> Unit): Modifier {
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = onHover(true)
            override fun onExited(event: DragAndDropEvent) = onHover(false)
            override fun onEnded(event: DragAndDropEvent) = onHover(false)
            override fun onDrop(event: DragAndDropEvent): Boolean {
                onHover(false)
                val transferable = (event.nativeEvent as? DropTargetDropEvent)?.transferable ?: return false
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                onFiles(files.filterIsInstance<File>())
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}
