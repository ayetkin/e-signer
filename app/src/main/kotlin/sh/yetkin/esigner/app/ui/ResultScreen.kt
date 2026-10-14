package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.FileResult
import sh.yetkin.esigner.app.i18n.str

@Composable
fun ResultScreen(state: AppState, results: List<FileResult>) {
    val succeeded = results.count { it.output != null }
    val allOk = succeeded == results.size
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(state)
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.TopCenter) {
            Panel(Modifier.widthIn(max = 760.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(56.dp).background(
                            if (allOk) MaterialTheme.colorScheme.tertiaryContainer else LocalExtraColors.current.warningContainer,
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (allOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (allOk) MaterialTheme.colorScheme.tertiary else LocalExtraColors.current.warning,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (allOk) str("result.allDone", results.size) else str("result.partial", succeeded, results.size),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        str("result.savedNextTo"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(results) { result ->
                        ResultRow(result)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    results.firstNotNullOfOrNull { it.output }?.let { first ->
                        OutlinedButton(onClick = { revealFile(first) }) { Text(str("result.showFolder")) }
                    }
                    Button(onClick = state::startOver) { Text(str("result.newSigning")) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: FileResult) {
    val output = result.output
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            if (output != null) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (output != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                output?.name ?: result.input.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    output != null -> str("result.source", result.input.name)
                    result.skipped -> str("result.skipped")
                    else -> result.error?.let { errorText(it) }.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (output != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        if (output != null) {
            TextButton(onClick = { openFile(output) }) { Text(str("result.open")) }
        }
    }
}
