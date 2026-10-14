package sh.yetkin.esigner.core

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignatureValidatorTest {
    private val dir = Files.createTempDirectory("validator-test").toFile()
    private val signer = DocumentSigner()

    @AfterTest
    fun cleanup() = dir.deleteRecursively().let { }

    private fun signedPdf(qualified: Boolean = false, seal: Boolean = false): File {
        val input = File(dir, "doc.pdf").also { file -> PDDocument().use { it.addPage(PDPage()); it.save(file) } }
        val token = if (seal) TestKeys.sealToken() else if (qualified) TestKeys.qualifiedToken() else TestKeys.token()
        return token.use { signer.sign(input, SignatureFormat.PADES, it, it.keys.single()) }
    }

    private fun signedImz(content: String): File {
        val input = File(dir, "notes.txt").apply { writeText(content) }
        return TestKeys.token().use { signer.sign(input, SignatureFormat.CADES, it, it.keys.single()) }
    }

    private fun validator(vararg trusted: java.security.cert.X509Certificate) =
        SignatureValidator(TrustAnchors(trusted.toList()), online = false)

    @Test
    fun `signature chained to a trust anchor is valid`() {
        val check = validator(TestKeys.certificate).validate(signedPdf()).signatures.single()
        assertEquals(Verdict.VALID, check.verdict, check.messages.joinToString())
        assertTrue(check.trustedChain)
        assertEquals("Test User", check.signer?.commonName)
        assertFalse(check.isLegallyValid, "no 5070 QC statement")
    }

    @Test
    fun `qualified certificate makes a legally valid signature`() {
        val check = validator(TestKeys.qualifiedCertificate).validate(signedPdf(qualified = true)).signatures.single()
        assertEquals(Verdict.VALID, check.verdict, check.messages.joinToString())
        assertTrue(check.signer!!.isQualified5070)
        assertTrue(check.isLegallyValid)
    }

    @Test
    fun `fiscal seal is recognized separately from a 5070 signature`() {
        val check = validator(TestKeys.sealCertificate).validate(signedPdf(seal = true)).signatures.single()
        assertEquals(Verdict.VALID, check.verdict, check.messages.joinToString())
        assertTrue(check.isValidFiscalSeal)
        assertFalse(check.isLegallyValid)
        assertEquals(IdType.TAX_ID, check.signer!!.idType)
        assertEquals("12******90", check.signer!!.maskedNationalId)
    }

    @Test
    fun `unknown root is indeterminate`() {
        val check = validator().validate(signedPdf()).signatures.single()
        assertEquals(Verdict.INDETERMINATE, check.verdict)
        assertFalse(check.trustedChain)
        assertEquals("NO_CERTIFICATE_CHAIN_FOUND", check.subIndication)
    }

    @Test
    fun `modified enveloped content is invalid`() {
        val imz = signedImz("pay 100")
        val bytes = imz.readBytes()
        val at = String(bytes, Charsets.ISO_8859_1).indexOf("pay 100")
        bytes[at + 4] = '9'.code.toByte()
        imz.writeBytes(bytes)
        val check = validator(TestKeys.certificate).validate(imz).signatures.single()
        assertEquals(Verdict.INVALID, check.verdict)
        assertEquals("HASH_FAILURE", check.subIndication)
    }

    @Test
    fun `modified pdf content is invalid`() {
        val pdf = signedPdf()
        val bytes = pdf.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        // Corrupt a byte inside the signed range, before the signature dictionary.
        val at = text.indexOf("/Type /Catalog").takeIf { it >= 0 } ?: text.indexOf("/Type/Catalog")
        bytes[at + 1] = 'X'.code.toByte()
        pdf.writeBytes(bytes)
        val check = validator(TestKeys.certificate).validate(pdf).signatures.single()
        assertEquals(Verdict.INVALID, check.verdict, check.messages.joinToString())
    }

    @Test
    fun `annotation added after signing is not legally valid`() {
        val signed = signedPdf(qualified = true)
        val tampered = File(dir, "tampered.pdf")
        PDDocument.load(signed).use { doc ->
            val page = doc.getPage(0)
            val note = PDAnnotationText().apply {
                rectangle = PDRectangle(100f, 100f, 200f, 50f)
                contents = "pay 1000000"
            }
            page.annotations = page.annotations + note
            // PDFBox 2 writes only objects flagged as changed, and only along a flagged path from the catalog.
            listOf(doc.documentCatalog.cosObject, doc.pages.cosObject, page.cosObject, note.cosObject)
                .forEach { it.isNeedToBeUpdated = true }
            tampered.outputStream().use { doc.saveIncremental(it) }
        }
        assertTrue("pay 1000000" in tampered.readText(Charsets.ISO_8859_1), "the annotation must reach the file")
        val check = validator(TestKeys.qualifiedCertificate).validate(tampered).signatures.single()
        assertTrue(check.modifiedAfterSigning, check.messages.joinToString())
        assertFalse(check.isLegallyValid)
    }

    @Test
    fun `a later signature keeps the earlier one legally valid`() {
        val first = signedPdf(qualified = true)
        val second = TestKeys.qualifiedToken().use { signer.sign(first, SignatureFormat.PADES, it, it.keys.single()) }
        val checks = validator(TestKeys.qualifiedCertificate).validate(second).signatures
        assertEquals(2, checks.size)
        assertTrue(checks.all { it.isLegallyValid }, checks.joinToString { it.messages.joinToString() })
    }

    @Test
    fun `enveloped content can be extracted`() {
        val imz = signedImz("hello world")
        val report = validator(TestKeys.certificate).validate(imz)
        assertTrue(report.hasEmbeddedContent)
        val target = File(dir, "extracted.txt")
        validator(TestKeys.certificate).extractOriginal(imz, target)
        assertEquals("hello world", target.readText())
        assertFailsWith<SigningException> { validator(TestKeys.certificate).extractOriginal(imz, target) }
    }

    @Test
    fun `unsigned file is reported`() {
        val plain = File(dir, "plain.txt").apply { writeText("not signed") }
        val e = assertFailsWith<SigningException> { validator().validate(plain) }
        assertEquals(SigningError.NOT_SIGNED, e.error)
        val pdf = File(dir, "plain.pdf").also { file -> PDDocument().use { it.addPage(PDPage()); it.save(file) } }
        assertTrue(validator().validate(pdf).signatures.isEmpty())
    }

    @Test
    fun `runtime modules needed for online validation are present`() {
        // Online CRL checks need a CRL parser implementation; offline tests never reach this class otherwise.
        Class.forName("eu.europa.esig.dss.crl.CRLUtils", true, javaClass.classLoader)
    }

    @Test
    fun `bundled trust anchors load and match their hashes`() {
        val anchors = TrustAnchors.bundled().certificates
        assertTrue(anchors.size >= 15)
        assertTrue(anchors.all { it.subjectX500Principal == it.issuerX500Principal })
    }
}
