package sh.yetkin.esigner.core

import eu.europa.esig.dss.cades.CAdESSignatureParameters
import eu.europa.esig.dss.cades.signature.CAdESService
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.enumerations.SignaturePackaging
import eu.europa.esig.dss.model.DSSDocument
import eu.europa.esig.dss.model.FileDocument
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.model.SerializableSignatureParameters
import eu.europa.esig.dss.model.ToBeSigned
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.SignatureFieldParameters
import eu.europa.esig.dss.pades.SignatureImageParameters
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.signature.DocumentSignatureService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import eu.europa.esig.dss.token.DSSPrivateKeyEntry
import eu.europa.esig.dss.token.SignatureTokenConnection
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument

/**
 * Signs files with a key on any [SignatureTokenConnection] (PKCS#11 card, or a software key in tests).
 *
 * The flow is the DSS three-step one: data to sign -> raw signature from the token -> signed document.
 * Keeping the token step separate is what will later allow server-prepared hashes signed on the client.
 */
class DocumentSigner {
    private val digestAlgorithm = DigestAlgorithm.SHA256
    private val verifier = CommonCertificateVerifier()

    fun sign(
        input: File,
        format: SignatureFormat,
        token: SignatureTokenConnection,
        key: DSSPrivateKeyEntry,
        output: File = outputFileFor(input, format),
    ): File {
        if (!input.isFile) throw SigningException(SigningError.FILE_NOT_FOUND, input.name)
        if (output.exists()) throw SigningException(SigningError.OUTPUT_EXISTS, output.name)

        val document = FileDocument(input)
        val signed = when (format) {
            SignatureFormat.PADES -> signWith(PAdESService(verifier), document, padesParameters(key, freeCornerX(input)), token, key)
            SignatureFormat.CADES -> signWith(CAdESService(verifier), document, cadesParameters(key), token, key)
        }
        writeAtomically(signed, output)
        return output
    }

    private fun <P : SerializableSignatureParameters> signWith(
        service: DocumentSignatureService<P, *>,
        document: DSSDocument,
        parameters: P,
        token: SignatureTokenConnection,
        key: DSSPrivateKeyEntry,
    ): DSSDocument {
        val toSign: ToBeSigned = fileStep { service.getDataToSign(document, parameters) }
        val value = try {
            token.sign(toSign, digestAlgorithm, key)
        } catch (e: Exception) {
            throw cardError(e)
        }
        return fileStep { service.signDocument(document, parameters, value) }
    }

    private fun padesParameters(key: DSSPrivateKeyEntry, widgetX: Float) = PAdESSignatureParameters().apply {
        signatureLevel = SignatureLevel.PAdES_BASELINE_B
        digestAlgorithm = this@DocumentSigner.digestAlgorithm
        signingCertificate = key.certificate
        certificateChain = key.certificateChain.toList()
        imageParameters = invisibleWidget(widgetX)
    }

    /**
     * A 1x1 pt fully transparent widget in the top-left corner instead of DSS's zero-size invisible one.
     * Some PDF readers ignore a zero-size signature widget on pages with fractional sizes (595.27 x 841.89,
     * the common A4 output) and show "Unsigned Signature Field". The document content is unchanged.
     */
    private fun invisibleWidget(x: Float) = SignatureImageParameters().apply {
        image = InMemoryDocument(transparentPixel, "invisible.png")
        fieldParameters = SignatureFieldParameters().apply {
            page = 1
            originX = x
            originY = 0f
            width = 1f
            height = 1f
        }
    }

    private fun cadesParameters(key: DSSPrivateKeyEntry) = CAdESSignatureParameters().apply {
        signatureLevel = SignatureLevel.CAdES_BASELINE_B
        signaturePackaging = SignaturePackaging.ENVELOPING
        digestAlgorithm = this@DocumentSigner.digestAlgorithm
        signingCertificate = key.certificate
        certificateChain = key.certificateChain.toList()
    }

    /**
     * First spot along the top edge of page 1 where the 1x1 widget overlaps no existing annotation, so earlier
     * signatures' widgets (DSS rejects overlapping fields) never block a new signature.
     */
    private fun freeCornerX(input: File): Float = runCatching {
        PDDocument.load(input).use { doc ->
            val page = doc.getPage(0)
            val box = page.cropBox
            val taken = page.annotations.mapNotNull { it.rectangle }
            generateSequence(0f) { it + 2f }.take(MAX_SLOTS).firstOrNull { x ->
                val left = box.lowerLeftX + x
                val top = box.upperRightY
                taken.none { r ->
                    r.lowerLeftX < left + 1.5f && r.upperRightX > left - 0.5f &&
                        r.lowerLeftY < top + 0.5f && r.upperRightY > top - 1.5f
                }
            }
        }
    }.getOrNull() ?: 0f

    private companion object {
        const val MAX_SLOTS = 200

        val transparentPixel: ByteArray by lazy {
            ByteArrayOutputStream().also { ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", it) }
                .toByteArray()
        }
    }

    private inline fun <T> fileStep(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw SigningException(SigningError.FILE_UNSUPPORTED, cause = e)
    }

    /** Writes next to the target and moves into place, so a failure never leaves a half-written output. */
    private fun writeAtomically(document: DSSDocument, output: File) {
        val temp = File.createTempFile(".${output.name}.", ".tmp", output.absoluteFile.parentFile)
        try {
            temp.outputStream().use { document.writeTo(it) }
            // No REPLACE_EXISTING: fails instead of overwriting a file created in the meantime.
            Files.move(temp.toPath(), output.toPath())
        } catch (e: Exception) {
            throw SigningException(SigningError.WRITE_FAILED, output.name, e)
        } finally {
            temp.delete()
        }
    }
}
