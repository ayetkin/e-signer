package sh.yetkin.esigner.core

import eu.europa.esig.dss.model.DSSException
import java.io.File
import java.io.IOException
import java.security.ProviderException
import javax.security.auth.login.FailedLoginException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CardErrorTest {
    /** The JDK's own PKCS#11 error, so a change in its message format fails here. Kotlin cannot see the package. */
    private fun pkcs11Error(code: Long) = Class.forName("sun.security.pkcs11.wrapper.PKCS11Exception")
        .getConstructor(Long::class.javaPrimitiveType, String::class.java).newInstance(code, null) as Exception

    /** A failed login as SunPKCS11 and DSS report it: the PKCS#11 code a few causes deep. */
    private fun loginFailure(code: Long) =
        DSSException("Unable to instantiate KeyStore", IOException("load failed", FailedLoginException().initCause(pkcs11Error(code))))

    @Test
    fun `pkcs11 codes become card errors`() {
        assertEquals(SigningError.PIN_INCORRECT, cardError(loginFailure(0xA0)).error)
        assertEquals(SigningError.PIN_LOCKED, cardError(loginFailure(0xA4)).error)
        assertEquals(SigningError.PIN_LENGTH, cardError(loginFailure(0xA2)).error)
        assertEquals(SigningError.CARD_NOT_FOUND, cardError(loginFailure(0xE0)).error, "CKR_TOKEN_NOT_PRESENT")
        assertEquals(SigningError.CARD_FAILED, cardError(loginFailure(0x05)).error, "CKR_GENERAL_ERROR")

        // The card pulled out while signing: the batch stops.
        val removed = cardError(ProviderException("sign failed", pkcs11Error(0x32)))
        assertEquals(SigningError.CARD_NOT_FOUND, removed.error)
        assertTrue(removed.stopsBatch)

        assertEquals(SigningError.LIBRARY_LOAD_FAILED, cardError(UnsatisfiedLinkError("wrong architecture")).error)
        val known = SigningException(SigningError.NO_SIGNING_KEY)
        assertSame(known, cardError(known))
    }

    @Test
    fun `a card session that fails to open zeroes the pin`() {
        val pin = "123456".toCharArray()
        val e = assertFailsWith<SigningException> { CardSession.open("/no/such/library.so", 1, pin) }
        assertEquals(SigningError.LIBRARY_NOT_FOUND, e.error)
        assertTrue(pin.all { it == '\u0000' })

        val junk = File.createTempFile("not-a-driver", ".so").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        try {
            val other = "654321".toCharArray()
            assertFailsWith<SigningException> { CardSession.open(junk.path, 1, other) }
            assertTrue(other.all { it == '\u0000' })
        } finally {
            junk.delete()
        }
    }
}
