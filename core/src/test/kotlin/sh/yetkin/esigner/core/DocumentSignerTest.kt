package sh.yetkin.esigner.core

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.apache.pdfbox.rendering.PDFRenderer
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    private fun pdf(name: String = "report.pdf") = File(dir, name).also { file ->
        PDDocument().use { it.addPage(PDPage()); it.save(file) }
    }

    private fun signatureCount(file: File) = PDDocument.load(file).use { it.signatureDictionaries.size }

    @Test
    fun `pades signs pdf and keeps existing signatures`() {
        val first = signer.sign(pdf(), SignatureFormat.PADES, token, key)
        assertEquals("report-signed.pdf", first.name)
        assertEquals(1, signatureCount(first))

        val second = signer.sign(first, SignatureFormat.PADES, token, key)
        assertEquals(2, signatureCount(second))
    }

    @Test
    fun `signature widget is never zero-size, even on fractional A4 pages`() {
        val input = File(dir, "a4.pdf").also { file ->
            PDDocument().use { it.addPage(PDPage(PDRectangle(595.27f, 841.89f))); it.save(file) }
        }
        val signed = signer.sign(input, SignatureFormat.PADES, token, key)
        PDDocument.load(signed).use { doc ->
            val field = doc.documentCatalog.acroForm.fields.filterIsInstance<PDSignatureField>().single()
            val rect = field.widgets.single().rectangle
            assertTrue(rect.width > 0f && rect.height > 0f, "zero-size widget: $rect")
            assertTrue(rect.width <= 1f && rect.height <= 1f, "widget must stay invisible: $rect")
            assertTrue(field.signature != null)
        }
    }

    @Test
    fun `visible stamp goes bottom-right of the last page and the next one does not overlap it`() {
        val input = File(dir, "two-pages.pdf").also { file ->
            PDDocument().use { it.addPage(PDPage()); it.addPage(PDPage()); it.save(file) }
        }
        val icon = ByteArrayOutputStream().also {
            ImageIO.write(java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val stamped = DocumentSigner(stamp = VisibleStamp("Electronically signed", icon))
        val first = stamped.sign(input, SignatureFormat.PADES, token, key)
        val second = stamped.sign(first, SignatureFormat.PADES, token, key, File(dir, "twice.pdf"))
        val raw = String(second.readBytes(), Charsets.ISO_8859_1)
        assertTrue(raw.contains("Inter-Regular") && !raw.contains("PTSerif"), "the stamp text uses the bundled Inter font")
        PDDocument.load(second).use { doc ->
            assertTrue(doc.getPage(0).annotations.isEmpty())
            val rects = doc.getPage(1).annotations.map { it.rectangle }
            assertEquals(2, rects.size)
            val (a, b) = rects
            assertEquals(VisibleStamp.DEFAULT_HEIGHT, a.height, 0.5f)
            assertTrue(a.width > 80f, "stamp too narrow: $a")
            assertTrue(a.upperRightX > doc.getPage(1).cropBox.width / 2 && a.upperRightY < 100f, "not bottom-right: $a")
            assertTrue(a.upperRightX <= b.lowerLeftX || b.upperRightX <= a.lowerLeftX, "stamps overlap: $a $b")
            // For a visual check of the layout.
            File("build/stamp").mkdirs()
            ImageIO.write(PDFRenderer(doc).renderImageWithDPI(1, 220f).getSubimage(900, 2100, 970, 270), "png", File("build/stamp/stamp.png"))
        }
    }

    @Test
    fun `text-only stamp has the same space left and right of the text`() {
        val signed = DocumentSigner(stamp = VisibleStamp("Digitally signed by", null)).sign(pdf(), SignatureFormat.PADES, token, key)
        PDDocument.load(signed).use { doc ->
            val rect = doc.getPage(0).annotations.single().rectangle
            val scale = 8f
            val image = PDFRenderer(doc).renderImage(0, scale)
            val pageHeight = doc.getPage(0).cropBox.height
            val left = (rect.lowerLeftX * scale).toInt()
            val right = (rect.upperRightX * scale).toInt()
            val top = ((pageHeight - rect.upperRightY) * scale).toInt()
            val bottom = ((pageHeight - rect.lowerLeftY) * scale).toInt()
            // Columns with dark text pixels, ignoring a frame line at the very edge.
            val ink = (left + 4 until right - 4).filter { x -> (top + 4 until bottom - 4).any { y -> image.getRGB(x, y) and 0xFF < 128 } }
            // For a visual check of the margins.
            File("build/stamp").mkdirs()
            ImageIO.write(image.getSubimage(left - 8, top - 8, right - left + 16, bottom - top + 16), "png", File("build/stamp/text-only.png"))
            val leftGap = (ink.first() - left) / scale
            val rightGap = (right - ink.last()) / scale
            assertEquals(leftGap, rightGap, 1f, "left $leftGap pt, right $rightGap pt")
        }
    }

    @Test
    fun `visible stamp goes where the user chose, never over another signature`() {
        val stamped = DocumentSigner(stamp = VisibleStamp("Electronically signed", null))
        val chosen = StampPosition(page = 1, x = 100f, y = 200f)
        val signed = stamped.sign(pdf(), SignatureFormat.PADES, token, key, stampAt = chosen)
        PDDocument.load(signed).use { doc ->
            val page = doc.getPage(0)
            val rect = page.annotations.single().rectangle
            assertEquals(100f, rect.lowerLeftX, 0.5f)
            assertEquals(page.cropBox.height - 200f, rect.upperRightY, 0.5f)
            val (width, height) = VisibleStamp("Electronically signed", null).size("Test User")
            assertTrue(!stampFits(doc, chosen.copy(x = 110f), width, height))
            assertTrue(stampFits(doc, chosen.copy(y = 300f), width, height))
        }
        val e = assertFailsWith<SigningException> {
            stamped.sign(signed, SignatureFormat.PADES, token, key, File(dir, "again.pdf"), chosen.copy(x = 110f))
        }
        assertEquals(SigningError.STAMP_POSITION_INVALID, e.error)
        assertFalse(File(dir, "again.pdf").exists())
    }

    @Test
    fun `cades envelopes any file and the content is recoverable`() {
        val input = File(dir, "notes.txt").apply { writeText("hello world") }
        val output = signer.sign(input, SignatureFormat.CADES, token, key)
        assertEquals("notes.txt.imz", output.name)

        val cms = CMSSignedData(output.readBytes())
        val signerInfo = cms.signerInfos.signers.single()
        assertTrue(signerInfo.verify(JcaSimpleSignerInfoVerifierBuilder().build(TestKeys.certificate)))
        assertContentEquals(input.readBytes(), cms.signedContent.content as ByteArray)
    }

    @Test
    fun `a fiscal seal signs pdf and other files`() {
        TestKeys.sealToken().use { seal ->
            val sealKey = seal.keys.single()
            val pdf = signer.sign(pdf(), SignatureFormat.PADES, seal, sealKey)
            assertEquals(1, signatureCount(pdf))

            val input = File(dir, "invoice.xml").apply { writeText("<invoice/>") }
            val cms = CMSSignedData(signer.sign(input, SignatureFormat.CADES, seal, sealKey).readBytes())
            assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(TestKeys.sealCertificate)))
            assertContentEquals(input.readBytes(), cms.signedContent.content as ByteArray)
        }
    }

    @Test
    fun `never overwrites an existing output`() {
        val input = pdf()
        val existing = outputFileFor(input, SignatureFormat.PADES).apply { writeText("old") }
        assertFailsWith<SigningException> { signer.sign(input, SignatureFormat.PADES, token, key) }
        assertEquals("old", existing.readText())
    }

    @Test
    fun `broken pdf is a file error, not a card error`() {
        val input = File(dir, "broken.pdf").apply { writeText("not a pdf") }
        val e = assertFailsWith<SigningException> { signer.sign(input, SignatureFormat.PADES, token, key) }
        assertTrue(!e.isCardError)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") || it.name.contains("signed") })
    }

    @Test
    fun `output names`() {
        assertEquals("a-imzali.pdf", outputFileFor(File("/x/a.pdf"), SignatureFormat.PADES, "-imzali").name)
        assertEquals("a.docx.imz", outputFileFor(File("/x/a.docx"), SignatureFormat.CADES).name)
    }

    @Test
    fun `format selection`() {
        assertEquals(SignatureFormat.PADES, formatFor(File("a.PDF"), FormatMode.AUTO))
        assertEquals(SignatureFormat.CADES, formatFor(File("a.docx"), FormatMode.AUTO))
        assertEquals(SignatureFormat.CADES, formatFor(File("a.pdf"), FormatMode.CADES_ONLY))
    }
}
