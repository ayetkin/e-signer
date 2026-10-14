package sh.yetkin.esigner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `unreadable certificates are skipped`() {
        val raw = Pkcs11Scanner.RawToken(2, "Reader 1", "AKIS", "model", 0, listOf(byteArrayOf(1, 2, 3)))
        val card = CardScanner.toDetectedCard(raw)
        assertNull(card.holder)
        assertEquals("AKIS", card.label)
    }
}
