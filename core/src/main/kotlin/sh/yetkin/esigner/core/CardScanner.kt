package sh.yetkin.esigner.core

import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant

enum class PinStatus {
    OK,

    /** At least one wrong PIN was entered since the last successful login. */
    COUNT_LOW,

    /** The next wrong PIN blocks the card. */
    FINAL_TRY,
    LOCKED,
}

/** A card found without logging in. [holder] comes from the card's public signing certificate, if readable. */
data class DetectedCard(
    val slotId: Long,
    val readerName: String,
    val label: String,
    val model: String,
    val pinStatus: PinStatus,
    val holder: CertificateInfo?,
) {
}

data class CardScan(val readerCount: Int, val cards: List<DetectedCard>)

object CardScanner {
    private const val CKF_USER_PIN_COUNT_LOW = 0x10000L
    private const val CKF_USER_PIN_FINAL_TRY = 0x20000L
    private const val CKF_USER_PIN_LOCKED = 0x40000L

    /** Lists readers and inserted cards. Never logs in and never touches the PIN. Single card thread only. */
    fun scan(libraryPath: String): CardScan {
        if (!File(libraryPath).isFile) {
            throw SigningException(SigningError.LIBRARY_NOT_FOUND, libraryPath)
        }
        val raw = try {
            Pkcs11Scanner.scan(libraryPath)
        } catch (e: Throwable) {
            if (e is Exception || e is LinkageError) throw cardError(e) else throw e
        }
        return CardScan(raw.readerCount(), raw.tokens().map(::toDetectedCard))
    }

    internal fun toDetectedCard(raw: Pkcs11Scanner.RawToken): DetectedCard {
        val factory = CertificateFactory.getInstance("X.509")
        val certificates = raw.certificates().mapNotNull { bytes ->
            runCatching { factory.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate }.getOrNull()
        }.map(CertificateInfo::from)
        val now = Instant.now()
        val holder = certificates.firstOrNull { it.isSigningCertificate && it.isValidAt(now) }
            ?: certificates.firstOrNull { it.isSigningCertificate }
            ?: certificates.firstOrNull()
        return DetectedCard(
            slotId = raw.slotId(),
            readerName = raw.readerName(),
            label = raw.label(),
            model = raw.model(),
            pinStatus = pinStatusOf(raw.flags()),
            holder = holder,
        )
    }

    internal fun pinStatusOf(flags: Long): PinStatus = when {
        flags and CKF_USER_PIN_LOCKED != 0L -> PinStatus.LOCKED
        flags and CKF_USER_PIN_FINAL_TRY != 0L -> PinStatus.FINAL_TRY
        flags and CKF_USER_PIN_COUNT_LOW != 0L -> PinStatus.COUNT_LOW
        else -> PinStatus.OK
    }
}
