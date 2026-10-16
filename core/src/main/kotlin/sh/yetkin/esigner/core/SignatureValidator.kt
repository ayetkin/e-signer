package sh.yetkin.esigner.core

import eu.europa.esig.dss.diagnostic.SignatureWrapper
import eu.europa.esig.dss.enumerations.Indication
import eu.europa.esig.dss.enumerations.TokenExtractionStrategy
import eu.europa.esig.dss.model.FileDocument
import eu.europa.esig.dss.validation.SignedDocumentValidator
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant

enum class Verdict {
    /** ETSI TOTAL_PASSED: intact, trusted chain, certificate valid and not revoked. */
    VALID,

    /** ETSI TOTAL_FAILED: modified content, broken signature, revoked certificate, ... */
    INVALID,

    /** ETSI INDETERMINATE: not provable now, e.g. untrusted root or revocation not reachable. */
    INDETERMINATE,
}

/**
 * A concrete reason behind a verdict, read from the DSS diagnostic data, so the UI can say what is wrong.
 * [laterChange]: a change in a later PDF revision rather than a broken signature.
 */
enum class SignatureProblem(val laterChange: Boolean = false) {
    /** The signed byte range or signature dictionary no longer matches the file: it was re-saved or edited in place. */
    BYTE_RANGE_INVALID,

    /** The signed content is not where the signature points. */
    CONTENT_MISSING,

    /** The signed content's digest does not match: the content changed after signing. */
    CONTENT_CHANGED,

    /** The signature value does not verify with the signer's certificate. */
    SIGNATURE_VALUE_INVALID,

    /** A later PDF revision changes what the pages show, or adds or removes pages; see [SignatureCheck.changedPages]. */
    PAGES_CHANGED(laterChange = true),

    /** A later PDF revision adds or changes annotations (comments, drawings, stamps). */
    ANNOTATIONS_CHANGED(laterChange = true),

    /** A later PDF revision makes other changes the validator cannot classify. */
    OTHER_CHANGES(laterChange = true),
}

data class SignatureCheck(
    val signer: CertificateInfo?,
    val verdict: Verdict,
    /** ETSI EN 319 102-1 sub-indication name (e.g. HASH_FAILURE), null when valid. */
    val subIndication: String?,
    /** The signing certificate chain ends in one of the [TrustAnchors]. */
    val trustedChain: Boolean,
    val claimedSigningTime: Instant?,
    /** Earliest signature timestamp, the proven signing time. */
    val timestampTime: Instant?,
    /** For example PAdES-BASELINE-B. */
    val format: String?,
    /** Validation messages from DSS (English), for a technical details view. */
    val messages: List<String>,
    /**
     * A later PDF revision adds annotations, changes what the pages show or makes unknown changes. The default
     * ETSI policy only warns about this; later signatures and form fills do not count.
     */
    val modifiedAfterSigning: Boolean = false,
    /** Common name of the time-stamping authority that issued the earliest timestamp. */
    val timestampAuthority: String? = null,
    /**
     * The earliest timestamp is intact and its authority chains to one of the [TrustAnchors]: an official
     * timestamp from an authorized provider, not one from an arbitrary server.
     */
    val trustedTimestamp: Boolean = false,
    /** Most serious first; empty when nothing specific was found. */
    val problems: List<SignatureProblem> = emptyList(),
    /** 1-based pages that [SignatureProblem.PAGES_CHANGED] concerns. */
    val changedPages: List<Int> = emptyList(),
) {
    /**
     * A secure electronic signature in the sense of Law 5070: valid by ETSI rules, chained to a
     * BTK-authorized provider's root, made with a qualified certificate and not modified afterwards.
     */
    val isLegallyValid: Boolean
        get() = verdict == Verdict.VALID && trustedChain && !modifiedAfterSigning && signer?.isQualified5070 == true

    /** A valid fiscal seal (mali muhur) of an organization, governed by the Tax Procedure Law, not Law 5070. */
    val isValidFiscalSeal: Boolean
        get() = verdict == Verdict.VALID && trustedChain && !modifiedAfterSigning && signer?.isFiscalSeal == true
}

data class ValidationReport(
    val file: File,
    val signatures: List<SignatureCheck>,
    /** Enveloping signature (.imz): the signed content is inside and can be extracted. */
    val hasEmbeddedContent: Boolean,
)

/**
 * Validates PAdES and CAdES signatures with the DSS default ETSI validation policy. Revocation (OCSP,
 * CRL) and missing intermediates (AIA) are fetched online unless [online] is false.
 */
class SignatureValidator(
    private val anchors: TrustAnchors = TrustAnchors.bundled(),
    private val online: Boolean = true,
) {
    private val loader = if (online) CachingDataLoader() else null

    fun validate(file: File): ValidationReport {
        val validator = validatorFor(file)
        val reports = try {
            validator.validateDocument()
        } catch (e: Exception) {
            throw SigningException(SigningError.FILE_UNSUPPORTED, file.name, e)
        } catch (e: LinkageError) {
            // A missing or broken DSS module (e.g. ExceptionInInitializerError) must not take the app down.
            throw SigningException(SigningError.UNEXPECTED, e.javaClass.simpleName, e)
        }
        val simple = reports.simpleReport
        val diagnostic = reports.diagnosticData
        val checks = simple.signatureIdList.map { id ->
            val signature = diagnostic.getSignatureById(id)
            val signingCertificate = signature?.signingCertificate?.binaries?.let(::parseCertificate)
            val indication = simple.getIndication(id)
            val timestamp = signature?.timestampList?.filter { it.productionTime != null }?.minByOrNull { it.productionTime }
            val pages = signature?.let(::changedPages).orEmpty()
            val problems = signature?.let { problemsOf(it, pages) }.orEmpty()
            SignatureCheck(
                signer = signingCertificate?.let(CertificateInfo::from),
                verdict = when (indication) {
                    Indication.TOTAL_PASSED, Indication.PASSED -> Verdict.VALID
                    Indication.TOTAL_FAILED, Indication.FAILED -> Verdict.INVALID
                    else -> Verdict.INDETERMINATE
                },
                subIndication = simple.getSubIndication(id)?.name,
                trustedChain = signature?.certificateChain?.any { it.isTrusted } == true,
                claimedSigningTime = signature?.claimedSigningTime?.toInstant(),
                timestampTime = timestamp?.productionTime?.toInstant(),
                format = simple.getSignatureFormat(id)?.toString(),
                messages = (simple.getAdESValidationErrors(id) + simple.getAdESValidationWarnings(id))
                    .map { it.value }.distinct(),
                modifiedAfterSigning = problems.any { it.laterChange },
                problems = problems,
                changedPages = pages,
                timestampAuthority = timestamp?.signingCertificate?.commonName,
                trustedTimestamp = timestamp != null && timestamp.isSignatureValid && timestamp.isMessageImprintDataIntact &&
                    timestamp.certificateChain.any { it.isTrusted },
            )
        }
        val embedded = checks.isNotEmpty() && !file.extension.equals("pdf", ignoreCase = true) &&
            runCatching { validator.getOriginalDocuments(simple.signatureIdList.first()).isNotEmpty() }.getOrDefault(false)
        return ValidationReport(file, checks, embedded)
    }

    /** Writes the signed content of an enveloping signature (.imz) to [target]; never overwrites. */
    fun extractOriginal(file: File, target: File) {
        if (target.exists()) throw SigningException(SigningError.OUTPUT_EXISTS, target.name)
        val validator = validatorFor(file)
        val id = validator.signatures.firstOrNull()?.id ?: throw SigningException(SigningError.NOT_SIGNED, file.name)
        val original = validator.getOriginalDocuments(id).firstOrNull()
            ?: throw SigningException(SigningError.NO_ORIGINAL_CONTENT, file.name)
        try {
            target.outputStream().use { original.writeTo(it) }
        } catch (e: Exception) {
            Files.deleteIfExists(target.toPath())
            throw SigningException(SigningError.WRITE_FAILED, target.name, e)
        }
    }

    private fun validatorFor(file: File): SignedDocumentValidator {
        if (!file.isFile) throw SigningException(SigningError.FILE_NOT_FOUND, file.name)
        val validator = try {
            SignedDocumentValidator.fromDocument(FileDocument(file))
        } catch (e: Exception) {
            throw SigningException(SigningError.NOT_SIGNED, file.name, e)
        }
        validator.setCertificateVerifier(certificateVerifier(anchors, online, loader))
        // Certificate binaries in the diagnostic data, to read the signer's name and QC statements.
        validator.setTokenExtractionStrategy(TokenExtractionStrategy.EXTRACT_CERTIFICATES_ONLY)
        return validator
    }

    private fun problemsOf(signature: SignatureWrapper, changedPages: List<Int>): List<SignatureProblem> = buildList {
        val revision = signature.pdfRevision
        val digests = signature.digestMatchers.orEmpty()
        // Each of these makes the next ones fail as well; only the first one is the cause.
        when {
            revision != null && (!revision.isSignatureByteRangeValid || !revision.isPdfSignatureDictionaryConsistent) ->
                add(SignatureProblem.BYTE_RANGE_INVALID)
            digests.any { !it.isDataFound } -> add(SignatureProblem.CONTENT_MISSING)
            digests.any { !it.isDataIntact } -> add(SignatureProblem.CONTENT_CHANGED)
            // Without the signer's certificate nothing was verified; the sub-indication says that instead.
            !signature.isSignatureIntact && signature.signingCertificate != null -> add(SignatureProblem.SIGNATURE_VALUE_INVALID)
        }
        if (changedPages.isNotEmpty()) add(SignatureProblem.PAGES_CHANGED)
        if (!signature.pdfAnnotationChanges.isNullOrEmpty()) add(SignatureProblem.ANNOTATIONS_CHANGED)
        if (!signature.pdfUndefinedChanges.isNullOrEmpty()) add(SignatureProblem.OTHER_CHANGES)
    }

    private fun changedPages(signature: SignatureWrapper): List<Int> =
        (signature.pdfVisualDifferenceConcernedPages.orEmpty() + signature.pdfPageDifferenceConcernedPages.orEmpty())
            .map { it.toInt() }.distinct().sorted()

    private fun parseCertificate(bytes: ByteArray): X509Certificate? = runCatching {
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }.getOrNull()
}
