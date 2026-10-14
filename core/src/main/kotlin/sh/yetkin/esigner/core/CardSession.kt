package sh.yetkin.esigner.core

import eu.europa.esig.dss.token.DSSPrivateKeyEntry
import eu.europa.esig.dss.token.PasswordInputCallback
import eu.europa.esig.dss.token.Pkcs11SignatureToken
import eu.europa.esig.dss.token.SignatureTokenConnection
import java.io.File
import java.time.Instant

data class SignerKey(val entry: DSSPrivateKeyEntry, val info: CertificateInfo)

object Pkcs11Library {
    fun defaultPath(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> "/usr/local/lib/libakisp11.dylib"
            os.contains("win") -> "C:\\Windows\\System32\\akisp11.dll"
            else -> "/usr/lib/libakisp11.so"
        }
    }
}

/**
 * An open, logged-in card. Not thread safe: use it from a single thread.
 *
 * Takes ownership of the PIN array and zeroes it on [close]. The PIN is kept until then because the
 * token may need it again within the session; it is never used for a second login attempt.
 */
class CardSession private constructor(
    private val token: Pkcs11SignatureToken,
    private val pin: CharArray,
    val keys: List<SignerKey>,
) : AutoCloseable {

    val connection: SignatureTokenConnection get() = token

    /** First valid signing (nonRepudiation) key, otherwise the first key. */
    val preferredKeyIndex: Int
        get() = keys.indexOfFirst { it.info.isSigningCertificate && it.info.isValidAt(Instant.now()) }
            .takeIf { it >= 0 } ?: 0

    override fun close() {
        try {
            token.close()
        } finally {
            pin.fill('\u0000')
        }
    }

    companion object {
        /**
         * Opens the card and logs in exactly once. On any failure the token is closed, the PIN zeroed and a
         * [SigningException] thrown; there is no retry, a wrong PIN must not be tried again automatically.
         *
         * [slotId] is the real PKCS#11 slot id from [CardScanner] (the first AKIS slot id is 1, not 0).
         */
        fun open(libraryPath: String, slotId: Long, pin: CharArray): CardSession {
            var token: Pkcs11SignatureToken? = null
            try {
                if (!File(libraryPath).isFile) {
                    throw SigningException(SigningError.LIBRARY_NOT_FOUND, libraryPath)
                }
                token = Pkcs11SignatureToken(libraryPath, PasswordInputCallback { pin }, slotId.toInt(), -1, "")
                val keys = token.keys.map { SignerKey(it, CertificateInfo.from(it.certificate.certificate)) }
                if (keys.isEmpty()) throw SigningException(SigningError.NO_SIGNING_KEY)
                return CardSession(token, pin, keys)
            } catch (e: Throwable) {
                try {
                    token?.close()
                } catch (_: Exception) {
                }
                pin.fill('\u0000')
                                if (e is Exception || e is LinkageError) throw cardError(e) else throw e
            }
        }
    }
}
