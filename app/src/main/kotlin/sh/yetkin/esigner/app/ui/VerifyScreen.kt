package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.yetkin.esigner.app.AppState
import sh.yetkin.esigner.app.UiError
import sh.yetkin.esigner.app.VerifyItem
import sh.yetkin.esigner.app.VerifyState
import sh.yetkin.esigner.app.contentName
import sh.yetkin.esigner.app.i18n.LocalStrings
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.IdType
import sh.yetkin.esigner.core.SignatureCheck
import sh.yetkin.esigner.core.SignatureProblem
import sh.yetkin.esigner.core.ValidationReport
import sh.yetkin.esigner.core.Verdict
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Content types opened directly from an .imz; anything else is only shown in its folder. */
private val SAFE_TO_OPEN = setOf("pdf", "txt", "xml", "png", "jpg", "jpeg", "docx", "xlsx", "pptx", "odt", "ods")

/** Overall state of a file, for the chip in its header. */
private enum class FileVerdict { LEGAL, ATTENTION, INVALID, UNSIGNED }

private fun ValidationReport.verdict(): FileVerdict = when {
    signatures.isEmpty() -> FileVerdict.UNSIGNED
    signatures.any { it.verdict == Verdict.INVALID } -> FileVerdict.INVALID
    signatures.all { it.isLegallyValid || it.isValidFiscalSeal } -> FileVerdict.LEGAL
    else -> FileVerdict.ATTENTION
}

@Composable
fun VerifyPane(state: AppState, hovering: Boolean, pickFiles: () -> Unit, saveFile: (File) -> File?, modifier: Modifier) {
    if (state.verifyItems.isEmpty()) {
        DropZone(
            hovering = hovering,
            title = str(if (hovering) "files.dropNow" else "verify.dropTitle"),
            body = str("verify.dropBody"),
            pickFiles = pickFiles,
            modifier = modifier,
        )
        return
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(str("verify.title", state.verifyItems.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    str(if (hovering) "files.dropNow" else "verify.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = state::clearVerify) { Text(str("files.clear")) }
            OutlinedButton(onClick = pickFiles) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(str("files.add"), modifier = Modifier.padding(start = 6.dp))
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.verifyItems, key = { it.file.path }) { item -> VerifyCard(state, item, saveFile) }
            item { LegalNote() }
        }
    }
}

@Composable
private fun VerifyCard(state: AppState, item: VerifyItem, saveFile: (File) -> File?) {
    val scope = rememberCoroutineScope()
    var actionError by remember(item.file) { mutableStateOf<UiError?>(null) }
    var saved by remember(item.file) { mutableStateOf<File?>(null) }
    Panel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExtensionBadge(item.file.extension)
            Column(Modifier.weight(1f)) {
                Text(item.file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.file.sizeLabel()}  ·  ${item.file.parent}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val report = (item.state as? VerifyState.Done)?.report
            report?.let { FileVerdictChip(it.verdict()) }
            // Content behind a broken signature is not what was signed: do not offer it as if it were.
            if (report?.hasEmbeddedContent == true && report.verdict() != FileVerdict.INVALID) {
                TextButton(onClick = {
                    scope.launch {
                        val (opened, error) = state.openContent(item.file)
                        actionError = error
                        // The name comes from the untrusted .imz: never hand an executable type to the OS to open.
                        opened?.let { if (it.extension.lowercase() in SAFE_TO_OPEN) openFile(it) else revealFile(it) }
                    }
                }) { Text(str("verify.openContent")) }
                TextButton(onClick = {
                    val target = saveFile(File(item.file.parentFile, contentName(item.file)))
                    if (target != null) scope.launch {
                        actionError = state.extractContent(item.file, target)
                        if (actionError == null) saved = target
                    }
                }) { Text(str("verify.saveContent")) }
            } else {
                TextButton(onClick = { openFile(item.file) }) { Text(str("files.view")) }
            }
            IconButton(onClick = { state.removeVerify(item.file) }) {
                Icon(Icons.Default.Close, contentDescription = str("files.remove"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val s = item.state) {
                VerifyState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        str("verify.running"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                is VerifyState.Failed -> ErrorBanner(s.error)
                is VerifyState.Done ->
                    if (s.report.signatures.isEmpty()) Banner(str("verify.noSignature"), Tone.INFO)
                    else s.report.signatures.forEachIndexed { index, check ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SignatureView(check)
                    }
            }
            actionError?.let { ErrorBanner(it) }
            saved?.let { Banner(str("verify.contentSaved", it.name), Tone.SUCCESS) }
        }
    }
}

@Composable
private fun FileVerdictChip(verdict: FileVerdict) {
    when (verdict) {
        FileVerdict.LEGAL -> Chip(str("verify.file.valid"), Tone.SUCCESS)
        FileVerdict.ATTENTION -> Chip(str("verify.file.attention"), Tone.WARNING)
        FileVerdict.INVALID -> Chip(str("verify.file.invalid"), Tone.ERROR)
        FileVerdict.UNSIGNED -> Chip(str("verify.file.unsigned"))
    }
}

/** Sub-indications that only restate a [SignatureProblem]: a broken format, digest or signature value. */
private val COVERED_BY_PROBLEMS = setOf("FORMAT_FAILURE", "HASH_FAILURE", "SIG_CRYPTO_FAILURE", "SIGNED_DATA_NOT_FOUND")

@Composable
private fun SignatureView(check: SignatureCheck) {
    val (tone, icon, headline) = when {
        check.isLegallyValid -> Triple(Tone.SUCCESS, Icons.Default.CheckCircle, str("verify.sig.legal"))
        check.isValidFiscalSeal -> Triple(Tone.SUCCESS, Icons.Default.CheckCircle, str("verify.sig.fiscalSeal"))
        check.verdict == Verdict.VALID && check.modifiedAfterSigning -> Triple(Tone.WARNING, Icons.Default.Warning, str("verify.sig.modified"))
        check.verdict == Verdict.VALID && !check.trustedChain -> Triple(Tone.WARNING, Icons.Default.Info, str("verify.sig.untrusted"))
        check.verdict == Verdict.VALID -> Triple(Tone.WARNING, Icons.Default.Info, str("verify.sig.notQualified"))
        check.verdict == Verdict.INVALID -> Triple(Tone.ERROR, Icons.Default.Warning, str("verify.sig.invalid"))
        else -> Triple(Tone.WARNING, Icons.Default.Warning, str("verify.sig.indeterminate"))
    }
    var details by remember(check) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatusIcon(icon, tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = toneAccent(tone))
            val strings = LocalStrings.current
            // The specific problems below say more than these generic codes, which then add nothing.
            check.subIndication?.takeUnless { check.problems.isNotEmpty() && it in COVERED_BY_PROBLEMS }?.let { code ->
                val key = "verify.reason.$code"
                Text(
                    if (strings.has(key)) strings[key] else strings["verify.reason.other", code],
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            check.problems.forEach { problem ->
                val key = "verify.problem.${problem.name}"
                Text(
                    "• " + if (problem == SignatureProblem.PAGES_CHANGED) strings[key, check.changedPages.joinToString(", ")] else strings[key],
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            val signer = check.signer
            if (signer != null) {
                InfoRow(str("verify.signer"), signer.commonName)
                signer.maskedNationalId?.let {
                    val label = when (signer.idType) {
                        IdType.NATIONAL_ID -> "verify.nationalId"
                        IdType.TAX_ID -> "verify.taxId"
                        IdType.OTHER -> "verify.otherId"
                    }
                    InfoRow(str(label), it)
                }
                InfoRow(str("verify.issuer"), signer.issuerName)
            }
            check.claimedSigningTime?.let { InfoRow(str("verify.claimedTime"), formatDateTime(it)) }
            val timestamp = check.timestampTime
            if (timestamp == null) {
                InfoRow(str("verify.timestamp"), str("verify.noTimestamp"))
            } else {
                // An official timestamp proves the signing time; it stands out in green.
                InfoRow(str("verify.timestamp"), formatDateTime(timestamp), if (check.trustedTimestamp) MaterialTheme.colorScheme.tertiary else null)
                check.timestampAuthority?.let { InfoRow(str("verify.tsa"), it) }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    signer?.isQualified5070 == true -> Chip(str("verify.chip.qualified"), Tone.SUCCESS)
                    signer?.isFiscalSeal == true -> Chip(str("verify.chip.fiscalSeal"), Tone.SUCCESS)
                    else -> Chip(str("verify.chip.notQualified"), Tone.WARNING)
                }
                if (check.trustedChain) Chip(str("verify.chip.trusted"), Tone.SUCCESS)
                else Chip(str("verify.chip.untrusted"), Tone.WARNING)
                when {
                    check.trustedTimestamp -> Chip(str("verify.chip.officialTimestamp"), Tone.SUCCESS)
                    timestamp != null -> Chip(str("verify.chip.untrustedTimestamp"), Tone.WARNING)
                }
                check.format?.let { Chip(it) }
            }
            if (check.messages.isNotEmpty()) {
                Text(
                    str(if (details) "common.hideDetails" else "common.showDetails"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clickable { details = !details },
                )
                if (details) {
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        check.messages.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tone: Tone) {
    Box(
        Modifier.size(36.dp).background(toneContainer(tone), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = toneAccent(tone), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color? = null) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp).width(150.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor ?: Color.Unspecified,
            fontWeight = if (valueColor != null) FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LegalNote() {
    Row(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(str("verify.legalNote"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun formatDateTime(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(LocalStrings.current.language.locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)

@Composable
private fun toneAccent(tone: Tone): Color = when (tone) {
    Tone.SUCCESS -> MaterialTheme.colorScheme.tertiary
    Tone.ERROR -> MaterialTheme.colorScheme.error
    Tone.WARNING -> LocalExtraColors.current.warning
    Tone.INFO -> MaterialTheme.colorScheme.primary
}

@Composable
private fun toneContainer(tone: Tone): Color = when (tone) {
    Tone.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
    Tone.ERROR -> MaterialTheme.colorScheme.errorContainer
    Tone.WARNING -> LocalExtraColors.current.warningContainer
    Tone.INFO -> MaterialTheme.colorScheme.secondaryContainer
}
