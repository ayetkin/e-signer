package sh.yetkin.esigner.app

import sh.yetkin.esigner.core.SigningLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SigningSettingsTest {
    @Test
    fun `the default reason follows the language until the user writes another`() {
        val settings = SigningSettings(persist = false)
        assertNull(settings.reason, "no reason saved: the default is used")
        settings.updatePdfDetails(" Signed with E-Signer ", "", "Signed with E-Signer")
        assertNull(settings.reason, "the untouched default stays the default")
        settings.updatePdfDetails("Approved", "Istanbul", "Signed with E-Signer")
        assertEquals("Approved", settings.reason)
        assertEquals("Istanbul", settings.location)
        settings.updatePdfDetails("Signed with E-Signer", "", "Signed with E-Signer")
        assertNull(settings.reason, "typing the default again restores it")
        settings.updatePdfDetails("", "", "Signed with E-Signer")
        assertEquals("", settings.reason, "an empty reason leaves it out of the PDF")
    }

    @Test
    fun `kamu sm needs its own authentication`() {
        assertTrue(SigningSettings.needsKamuSmAuth("https://ZD.kamusm.gov.tr/", TsaAuthType.BASIC))
        assertFalse(SigningSettings.needsKamuSmAuth(SigningSettings.KAMU_SM_URL, TsaAuthType.KAMU_SM))
        assertFalse(SigningSettings.needsKamuSmAuth("http://tsa.example.com", TsaAuthType.BASIC))
        assertFalse(SigningSettings.needsKamuSmAuth("http://notkamusm.gov.tr", TsaAuthType.BASIC))
        assertFalse(SigningSettings.needsKamuSmAuth("", TsaAuthType.BASIC))

        // Credentials are always needed: the default type is Kamu SM, and an address alone is not enough.
        val bare = SigningSettings(persist = false)
        bare.setForTest(SigningLevel.BASELINE_T, "http://tsa.example.com")
        assertTrue(bare.missingTsa)

        // Unsaved values for the dialog's check: complete only with an address, a user and a password.
        assertNull(bare.timestampFor("http://tsa.example.com", TsaAuthType.BASIC, "", "pw".toCharArray()))
        assertNull(bare.timestampFor("http://tsa.example.com", TsaAuthType.BASIC, "user", null))
        assertNull(bare.timestampFor("http://tsa.example.com", TsaAuthType.KAMU_SM, "abc", "pw".toCharArray()))
        assertNull(bare.timestampFor(SigningSettings.KAMU_SM_URL, TsaAuthType.BASIC, "user", "pw".toCharArray()))
        assertNotNull(bare.timestampFor(SigningSettings.KAMU_SM_URL, TsaAuthType.KAMU_SM, "12345", "pw".toCharArray()))

        val settings = SigningSettings(persist = false)
        settings.setForTest(SigningLevel.BASELINE_T, SigningSettings.KAMU_SM_URL, TsaAuthType.BASIC)
        assertTrue(settings.missingTsa, "signing stays blocked")
    }
}
