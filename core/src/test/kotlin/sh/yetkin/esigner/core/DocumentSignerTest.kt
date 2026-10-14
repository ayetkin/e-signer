package sh.yetkin.esigner.core

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentSignerTest {
    private val dir = Files.createTempDirectory("signer-test").toFile()
    private val signer = DocumentSigner()
    private val token = TestKeys.token()
    private val key = token.keys.single()

    @AfterTest
    fun cleanup() {
        token.close()
        dir.deleteRecursively()
    }

    private fun pdf(name: String = "belge.pdf") = File(dir, name).also { file ->
        PDDocument().use { it.addPage(PDPage()); it.save(file) }
    }

    private fun signatureCount(file: File) = PDDocument.load(file).use { it.signatureDictionaries.size }

    @Test
    fun `pades signs pdf and keeps existing signatures`() {
        val first = signer.sign(pdf(), SignatureFormat.PADES, token, key)
        assertEquals("belge-imzali.pdf", first.name)
        assertEquals(1, signatureCount(first))

        val second = signer.sign(first, SignatureFormat.PADES, token, key)
        assertEquals(2, signatureCount(second))
    }

    @Test
    fun `cades envelopes any file and the content is recoverable`() {
        val input = File(dir, "notlar.txt").apply { writeText("merhaba dünya") }
        val output = signer.sign(input, SignatureFormat.CADES, token, key)
        assertEquals("notlar.txt.imz", output.name)

        val cms = CMSSignedData(output.readBytes())
        val signerInfo = cms.signerInfos.signers.single()
        assertTrue(signerInfo.verify(JcaSimpleSignerInfoVerifierBuilder().build(TestKeys.certificate)))
        assertContentEquals(input.readBytes(), cms.signedContent.content as ByteArray)
    }

    @Test
    fun `never overwrites an existing output`() {
        val input = pdf()
        val existing = outputFileFor(input, SignatureFormat.PADES).apply { writeText("eski") }
        assertFailsWith<SigningException> { signer.sign(input, SignatureFormat.PADES, token, key) }
        assertEquals("eski", existing.readText())
    }

    @Test
    fun `broken pdf is a file error, not a card error`() {
        val input = File(dir, "bozuk.pdf").apply { writeText("not a pdf") }
        val e = assertFailsWith<SigningException> { signer.sign(input, SignatureFormat.PADES, token, key) }
        assertTrue(!e.isCardError)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") || it.name.contains("imzali") })
    }

    @Test
    fun `format selection`() {
        assertEquals(SignatureFormat.PADES, formatFor(File("a.PDF"), FormatMode.AUTO))
        assertEquals(SignatureFormat.CADES, formatFor(File("a.docx"), FormatMode.AUTO))
        assertEquals(SignatureFormat.CADES, formatFor(File("a.pdf"), FormatMode.CADES_ONLY))
    }
}
