package sh.yetkin.esigner.core

/**
 * An error with a message that can be shown to the user as is.
 * [isCardError] is true when the card or token failed (not the file), so a batch should stop.
 */
class SigningException(
    val userMessage: String,
    cause: Throwable? = null,
    val isCardError: Boolean = false,
) : Exception(userMessage, cause) {

    /** Technical cause chain for an "details" view. Never contains the PIN. */
    val technicalDetail: String?
        get() = cause?.let { root ->
            generateSequence(root) { it.cause }
                .joinToString("\n") { "${it.javaClass.simpleName}: ${it.message}" }
        }
}

/** Maps PKCS#11 failures (reported by SunPKCS11 as CKR_* codes somewhere in the cause chain) to user messages. */
internal fun cardError(e: Throwable): SigningException {
    if (e is SigningException) return e
    val text = generateSequence(e) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
    val message = when {
        "CKR_PIN_INCORRECT" in text ->
            "PIN hatalı. Her yanlış deneme kalan hakkı azaltır, tekrar denemeden önce PIN'i kontrol edin."
        "CKR_PIN_LOCKED" in text ->
            "Kartın PIN'i bloke olmuş. Blokeyi kaldırmak için kart yönetim aracını (PUK) kullanın."
        "CKR_PIN_LEN_RANGE" in text -> "PIN uzunluğu geçersiz."
        listOf("CKR_TOKEN_NOT_PRESENT", "CKR_DEVICE_REMOVED", "CKR_SLOT_ID_INVALID", "slotListIndex", "CKR_DEVICE_ERROR")
            .any { it in text } ->
            "Kart bulunamadı. Kart okuyucunun takılı ve kartın yerinde olduğundan emin olun."
        else -> "Kart işlemi başarısız oldu."
    }
    return SigningException(message, e, isCardError = true)
}
