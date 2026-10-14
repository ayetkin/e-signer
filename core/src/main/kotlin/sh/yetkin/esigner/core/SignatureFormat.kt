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

fun outputFileFor(input: File, format: SignatureFormat): File {
    val file = input.absoluteFile
    return when (format) {
        SignatureFormat.PADES -> File(file.parentFile, file.nameWithoutExtension + "-imzali.pdf")
        SignatureFormat.CADES -> File(file.parentFile, file.name + ".imz")
    }
}
