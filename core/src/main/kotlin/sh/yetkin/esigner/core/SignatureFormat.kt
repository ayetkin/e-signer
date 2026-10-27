package sh.yetkin.esigner.core

import java.io.File

enum class SignatureFormat {
    /** Signature embedded in the PDF as an incremental update; existing signatures are preserved. */
    PADES,

    /** Enveloping CAdES: file.ext -> file.ext.imz, works for any file type. */
    CADES,
}

enum class FormatMode {
    /** PDF files get PAdES, everything else CAdES. */
    AUTO,
    CADES_ONLY,
}

fun formatFor(file: File, mode: FormatMode): SignatureFormat =
    if (mode == FormatMode.AUTO && file.extension.equals("pdf", ignoreCase = true)) SignatureFormat.PADES
    else SignatureFormat.CADES

/** report.pdf -> report-signed.pdf in every UI language; report.docx -> report.docx.imz. */
fun outputFileFor(input: File, format: SignatureFormat): File {
    val file = input.absoluteFile
    return when (format) {
        SignatureFormat.PADES -> File(file.parentFile, file.nameWithoutExtension + "-signed.pdf")
        SignatureFormat.CADES -> File(file.parentFile, file.name + ".imz")
    }
}

/** ETSI baseline level of new signatures. */
enum class SigningLevel {
    /** Signature only; the signing time is a claim of the signer's computer. */
    BASELINE_B,

    /** Adds a signature timestamp from a TSA, proving the signature existed at that time. */
    BASELINE_T,

    /** B-T plus the certificate chains and OCSP/CRL responses, so the signature stays verifiable offline and after expiry. */
    BASELINE_LT;

    val needsTimestamp: Boolean get() = this != BASELINE_B
}
