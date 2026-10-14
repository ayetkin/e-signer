package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import sh.yetkin.esigner.app.Phase
import sh.yetkin.esigner.app.sizeLabel
import sh.yetkin.esigner.core.SignatureFormat
import sh.yetkin.esigner.core.formatFor
import java.io.File
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DropTargetDropEvent

@Composable
fun FilesStep(state: AppState, pickFiles: () -> Unit, modifier: Modifier = Modifier) {
    val editable = state.phase == Phase.Editing
    var hovering by remember { mutableStateOf(false) }

    StepCard(
        number = 1,
        title = "Dosyalar",
        modifier = modifier.fileDropTarget(
            onHover = { hovering = it },
            onFiles = state::addFiles,
        ),
        trailing = {
            if (state.files.isNotEmpty() && editable) {
                TextButton(onClick = pickFiles) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Dosya ekle", modifier = Modifier.padding(start = 6.dp))
                }
            }
        },
    ) {
        if (state.files.isEmpty()) {
            DropZone(hovering, pickFiles, Modifier.fillMaxSize())
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(state.files, key = { it.path }) { file ->
                    FileRow(
                        file = file,
                        format = formatFor(file, state.mode),
                        removable = editable,
                        onRemove = { state.removeFile(file) },
                    )
                }
            }
            Text(
                if (hovering) "Bırakın, listeye eklensin" else "Daha fazla dosyayı buraya sürükleyebilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = if (hovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DropZone(hovering: Boolean, pickFiles: () -> Unit, modifier: Modifier) {
    val stroke = if (hovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val fill = if (hovering) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
    Box(
        modifier
            .background(fill, MaterialTheme.shapes.medium)
            .drawBehind {
                drawRoundRect(
                    color = stroke,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = stroke,
                modifier = Modifier.size(36.dp),
            )
            Text("Dosyaları buraya sürükleyip bırakın", style = MaterialTheme.typography.titleSmall)
            Text(
                "PDF dosyaları kendi içine, diğer dosyalar .imz olarak imzalanır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = pickFiles, modifier = Modifier.padding(top = 6.dp)) { Text("Dosya seç") }
        }
    }
}

@Composable
private fun FileRow(file: File, format: SignatureFormat, removable: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        TextButton(onClick = { openFile(file) }) { Text("Görüntüle") }
        if (removable) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Listeden çıkar", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ExtensionBadge(extension: String) {
    Box(
        Modifier.width(44.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            extension.uppercase().take(4).ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun FormatChip(format: SignatureFormat) {
    Text(
        when (format) {
            SignatureFormat.PADES -> "PDF içine"
            SignatureFormat.CADES -> ".imz"
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
