package sh.yetkin.esigner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardScannerTest {
    @Test
    fun `pin status from token flags`() {
        assertEquals(PinStatus.OK, CardScanner.pinStatusOf(0x0D))
        assertEquals(PinStatus.COUNT_LOW, CardScanner.pinStatusOf(0x10000))
        assertEquals(PinStatus.FINAL_TRY, CardScanner.pinStatusOf(0x10000 or 0x20000))
        assertEquals(PinStatus.LOCKED, CardScanner.pinStatusOf(0x40000 or 0x20000))
    }

    @Test
    fun `holder comes from the public signing certificate`() {
        val raw = Pkcs11Scanner.RawToken(1, "Reader 0", "AKIS", "model", 0, listOf(TestKeys.certificate.encoded))
        val card = CardScanner.toDetectedCard(raw)
        assertEquals(1, card.slotId)
        assertEquals("Test User", card.holder?.commonName)
        assertEquals("12*******01", card.holder?.maskedNationalId)
    }

    @Test
    fun `a fiscal seal card shows the company`() {
        val raw = Pkcs11Scanner.RawToken(1, "Reader 0", "AKIS", "model", 0, listOf(TestKeys.sealCertificate.encoded))
        val holder = CardScanner.toDetectedCard(raw).holder!!
        assertEquals("Sample Company Inc", holder.commonName)
        assertEquals("12******90", holder.maskedNationalId)
        assertEquals(IdType.TAX_ID, holder.idType)
        assertTrue(holder.isFiscalSeal)
        assertFalse(holder.isSigningCertificate)
    }

    @Test
    fun `signer preference`() {
        val signing = CertificateInfo.from(TestKeys.certificate)
        val seal = CertificateInfo.from(TestKeys.sealCertificate)
        val other = signing.copy(commonName = "Other", isSigningCertificate = false)
        val expired = signing.copy(notAfter = signing.notBefore)
        assertEquals(1, CertificateInfo.preferredSigner(listOf(seal, signing)))
        assertEquals(1, CertificateInfo.preferredSigner(listOf(other, seal)))
        assertEquals(1, CertificateInfo.preferredSigner(listOf(expired, seal)))
        assertEquals(0, CertificateInfo.preferredSigner(listOf(expired, other)))
        assertEquals(-1, CertificateInfo.preferredSigner(listOf(other)))
    }

    @Test
    fun `unreadable certificates are skipped`() {
        val raw = Pkcs11Scanner.RawToken(2, "Reader 1", "AKIS", "model", 0, listOf(byteArrayOf(1, 2, 3)))
        val card = CardScanner.toDetectedCard(raw)
        assertNull(card.holder)
        assertEquals("AKIS", card.label)
    }
}
