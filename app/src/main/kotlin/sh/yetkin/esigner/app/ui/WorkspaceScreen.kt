package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.SignFlow
import sh.yetkin.esigner.app.stampIcon
import sh.yetkin.esigner.app.Tab
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.SignatureFormat
import sh.yetkin.esigner.core.formatFor
import org.jetbrains.skia.Image
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DropTargetDropEvent
import java.io.File

@Composable
fun WorkspaceScreen(state: AppState, pickFiles: () -> Unit, saveFile: (File) -> File?) {
    var hovering by remember { mutableStateOf(false) }
    var previewed by remember { mutableStateOf<File?>(null) }
    val verify = state.tab == Tab.VERIFY
    AppShell(state) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .fileDropTarget(
                    onHover = { hovering = it },
                    onFiles = { if (state.tab == Tab.VERIFY) state.addForVerify(it) else state.addFiles(it) },
                )
                // The title bar inset above already separates the panels from the window edge.
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = if (LocalTitleBarInset.current > 0.dp) 0.dp else 24.dp),
        ) {
            // Fades between the verify pane, the drop zone and the file list.
            val page = when {
                verify -> "verify"
                state.files.isEmpty() -> "drop"
                else -> "files"
            }
            Crossfade(page) { page ->
                when (page) {
                    "verify" -> VerifyPane(state, hovering, pickFiles, saveFile, Modifier.fillMaxSize())
                    "drop" -> DropZone(
                        hovering = hovering,
                        title = str(if (hovering) "files.dropNow" else "files.dropTitle"),
                        body = str("files.dropBody"),
                        pickFiles = pickFiles,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {
                        // The chosen PDF, or the first one; the preview disappears when no PDF is in the list.
                        val preview = previewed?.takeIf { it in state.files } ?: state.files.firstOrNull { it.isPdf }
                        val list = @Composable { modifier: Modifier ->
                            FileList(state, hovering, pickFiles, preview, { previewed = it }, modifier)
                        }
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            when {
                                preview == null -> list(Modifier.fillMaxSize())
                                // Side by side when there is room, otherwise the preview goes under the list.
                                maxWidth >= SIDE_BY_SIDE_MIN_WIDTH -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    list(Modifier.weight(1.2f).fillMaxHeight())
                                    key(preview) { PdfPreview(preview, stampPlacement(state, preview), Modifier.weight(3f).fillMaxHeight()) }
                                }
                                else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    list(Modifier.weight(2f).fillMaxWidth())
                                    key(preview) { PdfPreview(preview, stampPlacement(state, preview), Modifier.weight(3f).fillMaxWidth()) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!verify) BottomBar(state)
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
private fun FileList(
    state: AppState,
    hovering: Boolean,
    pickFiles: () -> Unit,
    previewed: File?,
    onPreview: (File) -> Unit,
    modifier: Modifier,
) {
    val editable = state.flow == SignFlow.Idle
    Panel(
        modifier.then(
            if (hovering) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier,
        ),
        padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().heightIn(min = PANEL_HEADER_HEIGHT).padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // A narrow list keeps only the icon on the add button.
            val compact = maxWidth < COMPACT_HEADER_WIDTH
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        str("files.count", state.files.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (editable) {
                        TextButton(onClick = state::clearFiles) { Text(str("files.clear")) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = pickFiles) {
                            Icon(Icons.Default.Add, contentDescription = str("files.add"), modifier = Modifier.size(18.dp))
                            if (!compact) Text(str("files.add"), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                if (hovering) {
                    Text(
                        str("files.dropNow"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(state.files, key = { it.path }) { file ->
                FileRow(
                    file,
                    formatFor(file, state.mode),
                    removable = editable,
                    onRemove = { state.removeFile(file) },
                    selected = file == previewed,
                    onSelect = if (file.isPdf) ({ onPreview(file) }) else null,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 60.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: File,
    format: SignatureFormat,
    removable: Boolean,
    onRemove: () -> Unit,
    selected: Boolean,
    onSelect: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(animateColorAsState(if (selected) LocalExtraColors.current.selection else Color.Transparent).value)
            .handCursor()
            // Single click previews a PDF, double click opens any file in its own app.
            .combinedClickable(onClick = { onSelect?.invoke() }, onDoubleClick = { openFile(file) })
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExtensionBadge(file.extension, small = true)
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormatChip(format)
                Text(file.sizeLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
fun ExtensionBadge(extension: String, small: Boolean = false) {
    val pdf = extension.equals("pdf", ignoreCase = true)
    Box(
        Modifier
            .size(width = if (small) 32.dp else 42.dp, height = if (small) 38.dp else 48.dp)
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
            fontSize = if (small) 9.sp else 11.sp,
        )
    }
}

@Composable
fun FormatChip(format: SignatureFormat) {
    Chip(str(if (format == SignatureFormat.PADES) "format.chip.pades" else "format.chip.cades"))
}

@Composable
private fun BottomBar(state: AppState) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val missing = state.settings.missingTsa
            Text(
                if (missing) str("level.tsaMissing") else str("sign.dialog.level", levelSummary(state.settings)),
                style = MaterialTheme.typography.bodySmall,
                color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val count = state.files.size
            Button(
                onClick = state::requestSign,
                enabled = state.canSign,
                modifier = Modifier.height(40.dp),
            ) {
                Icon(AppIcons.DrawFilled, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                Text(
                    if (count == 0) str("sign.button") else str("sign.buttonCount", count),
                    // Merged so the button's label nudge (see Buttons.kt) still applies.
                    style = LocalTextStyle.current.merge(MaterialTheme.typography.titleSmall),
                    modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                )
            }
        }
    }
}

/** Null when the PDF gets no visible signature: invisible style, or signed as a separate .imz. */
@Composable
private fun stampPlacement(state: AppState, file: File): StampPlacement? {
    val stamp = state.visibleStamp ?: return null
    if (formatFor(file, state.mode) != SignatureFormat.PADES) return null
    // The same seal the signature gets: green with a timestamp.
    val iconBytes = stamp.image ?: stampIcon
    val icon = remember(iconBytes) { Image.makeFromEncoded(iconBytes).toComposeImageBitmap() }
    return StampPlacement(
        stamp,
        state.signerName,
        icon,
        state.stampPositions[file],
        editable = state.flow == SignFlow.Idle,
        focused = state.stampFocused,
        onFocus = { state.stampFocused = true },
        onResize = state.settings::updateStampHeight,
    ) { state.placeStamp(file, it) }
}

/** Below this the preview goes under the file list instead of next to it. */
private val SIDE_BY_SIDE_MIN_WIDTH = 640.dp
private val COMPACT_HEADER_WIDTH = 340.dp

private val File.isPdf get() = extension.equals("pdf", ignoreCase = true)

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
