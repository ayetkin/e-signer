package sh.yetkin.esigner.core

import eu.europa.esig.dss.cades.CAdESSignatureParameters
import eu.europa.esig.dss.cades.signature.CAdESService
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.enumerations.SignaturePackaging
import eu.europa.esig.dss.model.DSSDocument
import eu.europa.esig.dss.model.FileDocument
import eu.europa.esig.dss.model.SerializableSignatureParameters
import eu.europa.esig.dss.model.ToBeSigned
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.signature.DocumentSignatureService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import eu.europa.esig.dss.token.DSSPrivateKeyEntry
import eu.europa.esig.dss.token.SignatureTokenConnection
import java.io.File
import java.nio.file.Files

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
            SignatureFormat.PADES -> signWith(PAdESService(verifier), document, padesParameters(key), token, key)
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

    private fun padesParameters(key: DSSPrivateKeyEntry) = PAdESSignatureParameters().apply {
        signatureLevel = SignatureLevel.PAdES_BASELINE_B
        digestAlgorithm = this@DocumentSigner.digestAlgorithm
        signingCertificate = key.certificate
        certificateChain = key.certificateChain.toList()
    }

    private fun cadesParameters(key: DSSPrivateKeyEntry) = CAdESSignatureParameters().apply {
        signatureLevel = SignatureLevel.CAdES_BASELINE_B
        signaturePackaging = SignaturePackaging.ENVELOPING
        digestAlgorithm = this@DocumentSigner.digestAlgorithm
        signingCertificate = key.certificate
        certificateChain = key.certificateChain.toList()
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
