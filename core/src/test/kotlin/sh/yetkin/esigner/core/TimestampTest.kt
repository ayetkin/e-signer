package sh.yetkin.esigner.core

import eu.europa.esig.dss.enumerations.DigestAlgorithm
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.bouncycastle.tsp.TimeStampToken
import org.bouncycastle.cms.CMSSignedData
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimestampTest {
    private val dir = Files.createTempDirectory("timestamp-test").toFile()
    private val pki = LocalPki()

    @AfterTest
    fun cleanup() {
        pki.close()
        dir.deleteRecursively()
    }

    private fun pdf() = File(dir, "doc.pdf").also { file -> PDDocument().use { it.addPage(PDPage()); it.save(file) } }

    private fun signer(level: SigningLevel, source: HttpTimestampSource? = HttpTimestampSource(pki.tsaUrl)) =
        DocumentSigner(level, source, certificateVerifier(pki.anchors, online = level == SigningLevel.BASELINE_LT))

    private fun sign(level: SigningLevel, input: File, format: SignatureFormat) =
        pki.signerToken().use { signer(level).sign(input, format, it, it.keys.single()) }

    private fun offlineValidator() = SignatureValidator(pki.anchors, online = false)

    private fun assertRecent(time: Instant?) {
        assertNotNull(time)
        assertTrue(Duration.between(time, Instant.now()).abs() < Duration.ofMinutes(5), "timestamp time $time")
    }

    @Test
    fun `pades baseline t carries a timestamp the validator reports`() {
        val before = Instant.now().minusSeconds(1)
        val check = offlineValidator().validate(sign(SigningLevel.BASELINE_T, pdf(), SignatureFormat.PADES)).signatures.single()
        assertEquals("PAdES-BASELINE-T", check.format)
        assertRecent(check.timestampTime)
        assertTrue(!check.timestampTime!!.isBefore(before.minusSeconds(1)))
        assertEquals(1, pki.tsaRequests.get())
    }

    @Test
    fun `cades baseline t carries a timestamp the validator reports`() {
        val input = File(dir, "notes.txt").apply { writeText("hello") }
        val check = offlineValidator().validate(sign(SigningLevel.BASELINE_T, input, SignatureFormat.CADES)).signatures.single()
        assertEquals("CAdES-BASELINE-T", check.format)
        assertRecent(check.timestampTime)
    }

    @Test
    fun `baseline b has no timestamp and does not call the tsa`() {
        val check = offlineValidator().validate(sign(SigningLevel.BASELINE_B, pdf(), SignatureFormat.PADES)).signatures.single()
        assertEquals("PAdES-BASELINE-B", check.format)
        assertNull(check.timestampTime)
        assertEquals(0, pki.tsaRequests.get())
    }

    @Test
    fun `baseline lt embeds revocation data and validates offline`() {
        val signed = sign(SigningLevel.BASELINE_LT, pdf(), SignatureFormat.PADES)
        assertTrue(pki.crlRequests.get() > 0, "CRL fetched while signing")
        val crlFetches = pki.crlRequests.get()

        val check = offlineValidator().validate(signed).signatures.single()
        assertEquals("PAdES-BASELINE-LT", check.format)
        assertEquals(Verdict.VALID, check.verdict, check.messages.joinToString())
        assertTrue(check.isLegallyValid)
        assertRecent(check.timestampTime)
        assertEquals(crlFetches, pki.crlRequests.get(), "offline validation uses the embedded CRL")
    }

    @Test
    fun `cades baseline lt validates offline`() {
        val input = File(dir, "notes.txt").apply { writeText("hello") }
        val check = offlineValidator().validate(sign(SigningLevel.BASELINE_LT, input, SignatureFormat.CADES)).signatures.single()
        assertEquals("CAdES-BASELINE-LT", check.format)
        assertEquals(Verdict.VALID, check.verdict, check.messages.joinToString())
    }

    @Test
    fun `timestamp level without a tsa is refused before the card is used`() {
        val e = assertFailsWith<SigningException> {
            pki.signerToken().use { DocumentSigner(SigningLevel.BASELINE_T, null).sign(pdf(), SignatureFormat.PADES, it, it.keys.single()) }
        }
        assertEquals(SigningError.TIMESTAMP_NOT_CONFIGURED, e.error)
    }

    @Test
    fun `unreachable tsa stops the batch and leaves no output`() {
        val input = pdf()
        val dead = HttpTimestampSource("http://127.0.0.1:1/tsa", timeout = Duration.ofSeconds(2))
        val e = assertFailsWith<SigningException> {
            pki.signerToken().use {
                DocumentSigner(SigningLevel.BASELINE_T, dead, certificateVerifier(pki.anchors, false))
                    .sign(input, SignatureFormat.PADES, it, it.keys.single())
            }
        }
        assertEquals(SigningError.TIMESTAMP_UNREACHABLE, e.error)
        assertTrue(e.stopsBatch)
        assertTrue(!e.isCardError)
        assertTrue(dir.listFiles()!!.none { it.name != input.name })
    }

    @Test
    fun `kamu sm identity header is accepted with the right password`() {
        LocalPki(kamuSmCustomer = 12345L to "s3cret").use { tsa ->
            val digest = MessageDigest.getInstance("SHA-256").digest("data".toByteArray())
            val ok = HttpTimestampSource(tsa.tsaUrl, TimestampAuth.KamuSm(12345L, "s3cret".toCharArray()))
                .getTimeStampResponse(DigestAlgorithm.SHA256, digest)
            val token = TimeStampToken(CMSSignedData(ok.bytes))
            assertContentEquals(digest, token.timeStampInfo.messageImprintDigest)

            val wrong = HttpTimestampSource(tsa.tsaUrl, TimestampAuth.KamuSm(12345L, "wrong".toCharArray()))
            val e = assertFailsWith<SigningException> { wrong.getTimeStampResponse(DigestAlgorithm.SHA256, digest) }
            assertEquals(SigningError.TIMESTAMP_REJECTED, e.error)
        }
    }

    @Test
    fun `kamu sm identity encoding`() {
        val imprint = ByteArray(32) { it.toByte() }
        val header = KamuSmIdentity.build(42L, "pw".toCharArray(), imprint, 100, SecureRandom())
        // SEQUENCE, then INTEGER 42
        assertTrue(header.startsWith("30"), header)
        assertTrue(header.matches(Regex("[0-9a-f]+")))
        val bytes = header.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val seq = org.bouncycastle.asn1.ASN1Sequence.getInstance(bytes)
        assertEquals(5, seq.size())
        assertEquals(42L, org.bouncycastle.asn1.ASN1Integer.getInstance(seq.getObjectAt(0)).value.toLong())
        assertEquals(100, org.bouncycastle.asn1.ASN1Integer.getInstance(seq.getObjectAt(2)).value.toInt())
        // 32-byte imprint + PKCS#7 padding = 48 bytes
        assertEquals(48, org.bouncycastle.asn1.ASN1OctetString.getInstance(seq.getObjectAt(4)).octets.size)
    }

    @Test
    fun `credentials never appear in toString`() {
        val auth = TimestampAuth.KamuSm(7L, "topsecret".toCharArray())
        assertTrue("topsecret" !in auth.toString())
        assertTrue("topsecret" !in TimestampAuth.Basic("u", "topsecret".toCharArray()).toString())
        auth.clear()
        assertTrue(auth.password.all { it == '\u0000' })
    }
}
