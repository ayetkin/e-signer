package sh.yetkin.esigner.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CertificateInfoTest {
    @Test
    fun `masks national id`() {
        assertEquals("12*******01", maskNationalId("12345678901"))
        assertEquals("****", maskNationalId("1234"))
    }

    @Test
    fun `reads display fields and never exposes the full national id`() {
        val info = CertificateInfo.from(TestKeys.certificate)
        assertEquals("Test Kullanıcı", info.commonName)
        assertEquals("12*******01", info.maskedNationalId)
        assertEquals("Test Kullanıcı", info.issuerName)
        assertTrue(info.isSigningCertificate)
        assertTrue(info.isValidAt(Instant.now()))
        assertTrue(TestKeys.FAKE_NATIONAL_ID !in info.toString())
    }
}
