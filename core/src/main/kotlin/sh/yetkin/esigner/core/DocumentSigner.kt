package sh.yetkin.esigner.core

import eu.europa.esig.dss.alert.exception.AlertException
import eu.europa.esig.dss.cades.CAdESSignatureParameters
import eu.europa.esig.dss.cades.signature.CAdESService
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.enumerations.ImageScaling
import eu.europa.esig.dss.enumerations.SignaturePackaging
import eu.europa.esig.dss.enumerations.SignerTextPosition
import eu.europa.esig.dss.enumerations.TextWrapping
import eu.europa.esig.dss.model.DSSDocument
import eu.europa.esig.dss.model.FileDocument
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.model.SerializableSignatureParameters
import eu.europa.esig.dss.model.ToBeSigned
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.SignatureFieldParameters
import eu.europa.esig.dss.pades.SignatureImageParameters
import eu.europa.esig.dss.pades.SignatureImageTextParameters
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.signature.DocumentSignatureService
import eu.europa.esig.dss.spi.validation.CertificateVerifier
import eu.europa.esig.dss.spi.x509.tsp.TSPSource
import eu.europa.esig.dss.token.DSSPrivateKeyEntry
import eu.europa.esig.dss.token.SignatureTokenConnection
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Date
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument

/**
 * Signs files with a key on any [SignatureTokenConnection] (PKCS#11 card, or a software key in tests).
 *
 * The flow is the DSS three-step one: data to sign -> raw signature from the token -> signed document.
 * Keeping the token step separate is what will later allow server-prepared hashes signed on the client.
 *
 * [level] B-T and B-LT need a [timestampSource]; the timestamp is requested in the last step, after the
 * card has signed. B-LT also embeds the chains and OCSP/CRL responses for the signer and the TSA, fetched
 * online through [verifier] and checked against the bundled trust anchors.
 *
 * [stamp] draws a visible signature on PDFs, where the user chose or by default bottom-right on the last page;
 * without it the signature is invisible.
 */
class DocumentSigner(
    private val level: SigningLevel = SigningLevel.BASELINE_B,
    private val timestampSource: TSPSource? = null,
    private val verifier: CertificateVerifier =
        certificateVerifier(TrustAnchors.bundled(), online = level == SigningLevel.BASELINE_LT),
    private val stamp: VisibleStamp? = null,
    /** Optional /Reason and /Location of PDF signatures, shown in PDF readers' signature panels; blank is left out. */
    private val reason: String? = null,
    private val location: String? = null,
) {
    private val digestAlgorithm = DigestAlgorithm.SHA256

    fun sign(
        input: File,
        format: SignatureFormat,
        token: SignatureTokenConnection,
        key: DSSPrivateKeyEntry,
        output: File = outputFileFor(input, format),
        stampAt: StampPosition? = null,
    ): File {
        if (!input.isFile) throw SigningException(SigningError.FILE_NOT_FOUND, input.name)
        if (output.exists()) throw SigningException(SigningError.OUTPUT_EXISTS, output.name)
        if (level.needsTimestamp && timestampSource == null) throw SigningException(SigningError.TIMESTAMP_NOT_CONFIGURED)

        val document = FileDocument(input)
        val signed = when (format) {
            SignatureFormat.PADES -> signWith(
                PAdESService(verifier).apply { timestampSource?.let { setTspSource(it) } },
                document, padesParameters(key, input, stampAt), token, key,
            )
            SignatureFormat.CADES -> signWith(
                CAdESService(verifier).apply { timestampSource?.let { setTspSource(it) } },
                document, cadesParameters(key), token, key,
            )
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

    private fun padesParameters(key: DSSPrivateKeyEntry, input: File, stampAt: StampPosition?) = PAdESSignatureParameters().apply {
        signatureLevel = when (level) {
            SigningLevel.BASELINE_B -> SignatureLevel.PAdES_BASELINE_B
            SigningLevel.BASELINE_T -> SignatureLevel.PAdES_BASELINE_T
            SigningLevel.BASELINE_LT -> SignatureLevel.PAdES_BASELINE_LT
        }
        digestAlgorithm = this@DocumentSigner.digestAlgorithm
        signingCertificate = key.certificate
        certificateChain = key.certificateChain.toList()
        imageParameters = stamp?.let { visibleWidget(it, key, bLevel().signingDate, input, stampAt) } ?: invisibleWidget(input)
        this@DocumentSigner.reason?.trim()?.takeIf { it.isNotEmpty() }?.let { reason = it }
        this@DocumentSigner.location?.trim()?.takeIf { it.isNotEmpty() }?.let { location = it }
    }

    /**
     * A 1x1 pt fully transparent widget in the top-left corner instead of DSS's zero-size invisible one.
     * Some PDF readers ignore a zero-size signature widget on pages with fractional sizes (595.27 x 841.89,
     * the common A4 output) and show "Unsigned Signature Field". The document content is unchanged.
     */
    private fun invisibleWidget(input: File) = SignatureImageParameters().apply {
        image = InMemoryDocument(transparentPixel, "invisible.png")
        // The first spot along the top edge of page 1 free of earlier signatures' widgets.
        val position = withPdf(input) { doc ->
            generateSequence(0f) { it + 2f }.take(MAX_SLOTS).map { StampPosition(1, it, 0f) }
                .firstOrNull { stampFits(doc, it, 1f, 1f) }
        }
        fieldParameters = field(position ?: StampPosition(1, 0f, 0f), 1f, 1f)
    }

    /** The image and the label, signer's name and signing time at [stampAt], or the default spot. */
    private fun visibleWidget(stamp: VisibleStamp, key: DSSPrivateKeyEntry, signingDate: Date, input: File, stampAt: StampPosition?) =
        SignatureImageParameters().apply {
            val name = CertificateInfo.from(key.certificate.certificate).commonName
            stamp.widgetImage()?.let {
                image = InMemoryDocument(it, "stamp.png")
                imageScaling = ImageScaling.ZOOM_AND_CENTER
            }
            stamp.text(name, signingDate.toInstant())?.let {
                textParameters = SignatureImageTextParameters().apply {
                    text = it
                    font = VisibleStamp.dssFont()
                    textWrapping = TextWrapping.FILL_BOX
                    signerTextPosition = if (stamp.image != null) SignerTextPosition.RIGHT else SignerTextPosition.LEFT
                    padding = stamp.padding
                }
            }
            val (width, height) = stamp.size(name)
            // The chosen spot, or the default one when the page has room; never a spot DSS would reject.
            val position = withPdf(input) { doc ->
                (stampAt ?: defaultStampPosition(doc, width, height)).takeIf { stampFits(doc, it, width, height) }
                    ?: throw SigningException(SigningError.STAMP_POSITION_INVALID, input.name)
            } ?: throw SigningException(SigningError.FILE_UNSUPPORTED, input.name)
            // The spot was checked for any signing time; the box itself is as wide as the text at this one.
            fieldParameters = field(position, stamp.size(name, signingDate.toInstant()).first, height)
        }

    private fun field(position: StampPosition, width: Float, height: Float) = SignatureFieldParameters().apply {
        page = position.page
        originX = position.x
        originY = position.y
        this.width = width
        this.height = height
    }

    /** Null when PDFBox cannot open the file; DSS then reports it as unsupported. */
    private fun <T> withPdf(input: File, block: (PDDocument) -> T): T? =
        runCatching { PDDocument.load(input) }.getOrNull()?.use(block)

    private fun cadesParameters(key: DSSPrivateKeyEntry) = CAdESSignatureParameters().apply {
        signatureLevel = when (level) {
            SigningLevel.BASELINE_B -> SignatureLevel.CAdES_BASELINE_B
            SigningLevel.BASELINE_T -> SignatureLevel.CAdES_BASELINE_T
            SigningLevel.BASELINE_LT -> SignatureLevel.CAdES_BASELINE_LT
        }
        signaturePackaging = SignaturePackaging.ENVELOPING
        digestAlgorithm = this@DocumentSigner.digestAlgorithm
        signingCertificate = key.certificate
        certificateChain = key.certificateChain.toList()
    }

    private companion object {
        const val MAX_SLOTS = 200

        val transparentPixel: ByteArray by lazy {
            ByteArrayOutputStream().also { ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", it) }
                .toByteArray()
        }
    }

    /**
     * Document steps. A timestamp failure surfaces from inside DSS, possibly wrapped; a DSS alert during
     * B-LT means revocation data was missing. Anything else is a problem with the file.
     */
    private inline fun <T> fileStep(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        val chain = generateSequence<Throwable>(e) { it.cause }
        chain.filterIsInstance<SigningException>().firstOrNull()?.let { throw it }
        val alert = chain.filterIsInstance<AlertException>().firstOrNull()
        if (alert != null && level == SigningLevel.BASELINE_LT && "revocation" in alert.message.orEmpty().lowercase()) {
            throw SigningException(SigningError.REVOCATION_UNAVAILABLE, cause = e)
        }
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

