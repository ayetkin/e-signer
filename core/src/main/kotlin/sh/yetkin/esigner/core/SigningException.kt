package sh.yetkin.esigner.core

/**
 * Error codes; the UI maps them to localized text. [stopsBatch]: the remaining files of a batch would
 * fail the same way (card or timestamp service problem), so they are not attempted.
 */
enum class SigningError(val isCardError: Boolean, val stopsBatch: Boolean) {
    LIBRARY_NOT_FOUND(false),
    LIBRARY_LOAD_FAILED(false),
    PIN_INCORRECT(true),
    PIN_LOCKED(true),
    PIN_LENGTH(true),
    CARD_NOT_FOUND(true),
    NO_SIGNING_KEY(true),
    CARD_FAILED(true),
    /** The PKCS#11 module did not answer in time, e.g. it hangs after the card was pulled out. */
    CARD_NOT_RESPONDING(true),
    FILE_NOT_FOUND(false),
    OUTPUT_EXISTS(false),
    FILE_UNSUPPORTED(false),
    WRITE_FAILED(false),
    /** The chosen place for the visible signature overlaps another signature or leaves the page. */
    STAMP_POSITION_INVALID(false),
    UNEXPECTED(false),
    NOT_SIGNED(false),
    NO_ORIGINAL_CONTENT(false),
    TIMESTAMP_NOT_CONFIGURED(false, stopsBatch = true),
    /** Network error, timeout or invalid URL. */
    TIMESTAMP_UNREACHABLE(false, stopsBatch = true),
    /** Wrong credentials or no remaining balance. */
    TIMESTAMP_REJECTED(false, stopsBatch = true),
    TIMESTAMP_FAILED(false, stopsBatch = true),
    /** B-LT: certificate status (OCSP/CRL) could not be fetched for the signer or the TSA. */
    REVOCATION_UNAVAILABLE(false, stopsBatch = true);

    constructor(isCardError: Boolean) : this(isCardError, isCardError)
}

/**
 * A failure with a code the UI can translate. [subject] is an optional value for the message (a file
 * name or a library path). When [isCardError] is true the card failed, not the file, so a batch should stop.
 */
class SigningException(
    val error: SigningError,
    val subject: String? = null,
    cause: Throwable? = null,
) : Exception(if (subject == null) error.name else "${error.name}: $subject", cause) {

    val isCardError: Boolean get() = error.isCardError

    val stopsBatch: Boolean get() = error.stopsBatch

    /**
     * Console-style report for the "technical details" view: the error code, the cause chain and the
     * deepest cause's top stack frames. Never contains the PIN (PINs are never put in messages).
     */
    val technicalDetail: String
        get() = buildString {
            append(message)
            val chain = generateSequence(cause) { it.cause }.toList()
            chain.forEach { append("\nCaused by: ${it.javaClass.name}: ${it.message}") }
            chain.lastOrNull()?.stackTrace?.take(12)?.forEach { append("\n    at $it") }
        }
}

/** Maps PKCS#11 failures (SunPKCS11 reports CKR_* codes somewhere in the cause chain) to error codes. */
internal fun cardError(e: Throwable): SigningException {
    if (e is SigningException) return e
    if (e is LinkageError) return SigningException(SigningError.LIBRARY_LOAD_FAILED, cause = e)
    val text = generateSequence(e) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
    val error = when {
        "CKR_PIN_INCORRECT" in text -> SigningError.PIN_INCORRECT
        "CKR_PIN_LOCKED" in text -> SigningError.PIN_LOCKED
        "CKR_PIN_LEN_RANGE" in text -> SigningError.PIN_LENGTH
        listOf("CKR_TOKEN_NOT_PRESENT", "CKR_DEVICE_REMOVED", "CKR_SLOT_ID_INVALID", "slotListIndex", "CKR_DEVICE_ERROR")
            .any { it in text } -> SigningError.CARD_NOT_FOUND
        else -> SigningError.CARD_FAILED
    }
    return SigningException(error, cause = e)
}
