package sh.yetkin.esigner.core

import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.pades.DSSFileFont
import eu.europa.esig.dss.pdf.AnnotationBox
import eu.europa.esig.dss.pdf.pdfbox.visible.nativedrawer.PdfBoxDSSFontMetrics
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.PDDocument
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Top-left corner of a signature widget: [page] is 1-based, [x] and [y] are points from the top-left corner
 * of the page's crop box (the coordinates DSS uses).
 */
data class StampPosition(val page: Int, val x: Float, val y: Float)

/**
 * A visible signature on the PDF: [label] (e.g. "Electronically signed") over the signer's name and the signing
 * time, and/or [image] (PNG bytes) to its left. At least one of them must be set. [height] is in points; the
 * width follows from it and the text.
 */
class VisibleStamp(val label: String?, val image: ByteArray?, val height: Float = DEFAULT_HEIGHT) {
    init {
        require(label != null || image != null)
        require(height > 0f)
    }

    /** Space around the text, in points. */
    val padding: Float get() = height / 20f

    /** Side of the (square) image in points: the full height alone, smaller next to the text so it does not dominate. */
    val iconSize: Float get() = if (label != null) height * ICON_RATIO else height

    /** Width of the image part: the margin to the frame, the icon, and the gap to the text when there is one. */
    val imageWidth: Float get() = if (image == null) 0f else if (label != null) height * (EDGE_RATIO + ICON_RATIO + GAP_RATIO) else height

    /** Space between the frame and the icon, in points. */
    val iconInset: Float get() = if (label != null) height * EDGE_RATIO else 0f

    /**
     * The image as drawn in the widget. DSS zooms the image to fill its part of the box, so the smaller icon and
     * the margins are transparent space around the picture: above and below it, and on both sides.
     */
    internal fun widgetImage(): ByteArray? {
        if (image == null || label == null) return image
        val source = ImageIO.read(ByteArrayInputStream(image)) ?: return image
        val canvasHeight = (source.height / ICON_RATIO).roundToInt()
        val canvasWidth = (canvasHeight * (EDGE_RATIO + ICON_RATIO + GAP_RATIO)).roundToInt()
        val padded = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
        padded.createGraphics().apply {
            drawImage(source, (canvasHeight * EDGE_RATIO).roundToInt(), (canvasHeight - source.height) / 2, null)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(padded, "png", it) }.toByteArray()
    }

    /** The lines next to the image, or null without a label. */
    fun text(signerName: String, time: Instant): String? =
        label?.let { "$it\n$signerName\n${TIME_FORMAT.format(time.atZone(ZoneId.systemDefault()))}" }

    /**
     * Width and height in points for [signerName]. The text part is as wide as its longest line at the font size
     * that makes the lines fill the height, so DSS's FILL_BOX layout adds no space of its own. Without a [time]
     * the width fits any signing time (the space a stamp needs on the page); with it, the exact text at that time
     * (the box as drawn, with the same margin on both sides).
     */
    fun size(signerName: String, time: Instant? = null): Pair<Float, Float> {
        val textWidth = text(signerName, time ?: Instant.now())?.let { fittedWidth(it, anyTime = time == null) } ?: 0f
        return imageWidth + textWidth to height
    }

    /** Font size in points at which the label's lines fill the height, as DSS draws them. */
    fun fontSize(signerName: String): Float = text(signerName, Instant.now())?.let { SAMPLE_SIZE * fillScale(it) } ?: 0f

    private fun fittedWidth(text: String, anyTime: Boolean): Float {
        // For any time, every digit as the widest one, so the box fits the signing time whatever it turns out to be.
        val widest = textBox(if (anyTime) text.replace(DIGIT, widestDigit) else text).width
        // Next to an icon the right margin matches the icon's margin to the frame; text alone keeps the same
        // padding on both sides.
        return widest * fillScale(text) + 2 * padding + if (image != null) height * RIGHT_RATIO else 0f
    }

    /** How much the sample font size must grow for the lines to fill the height inside the padding. */
    private fun fillScale(text: String): Float = (height - 2 * padding) / textBox(text).height

    companion object {
        const val DEFAULT_HEIGHT = 35f

        const val MIN_HEIGHT = 16f
        const val MAX_HEIGHT = 80f

        /** Margin to the frame, icon side and gap to the text next to it, as fractions of the height. */
        private const val EDGE_RATIO = 0.08f
        private const val ICON_RATIO = 0.8f
        private const val GAP_RATIO = 0.12f

        /** Extra space between the text and the right edge of the frame next to an icon, as a fraction of the height. */
        private const val RIGHT_RATIO = 0.08f
        private const val SAMPLE_SIZE = 10f
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

        /**
         * DSS's own metrics for the stamp font (Inter Regular, SIL Open Font License 1.1, fonts/Inter-LICENSE.txt),
         * the ones its FILL_BOX layout sizes the text with; Java's metrics for the same font differ by a third.
         */
        private val metrics by lazy { PdfBoxDSSFontMetrics(PDType0Font.load(PDDocument(), ByteArrayInputStream(fontData))) }

        /** Lines of [text] at the sample size, in points. */
        private fun textBox(text: String): AnnotationBox = synchronized(metrics) { metrics.computeTextBoundaryBox(text, SAMPLE_SIZE) }

        private val DIGIT = Regex("[0-9]")
        private val widestDigit by lazy { ('0'..'9').maxBy { textBox(it.toString()).width }.toString() }

        /** That font's file, so a preview can draw the text exactly as the signed PDF will. */
        val fontData: ByteArray by lazy { VisibleStamp::class.java.getResourceAsStream("/fonts/Inter-Regular.ttf")!!.use { it.readBytes() } }

        /** The font for DSS, a new one per signature. */
        internal fun dssFont() = DSSFileFont(InMemoryDocument(fontData, "Inter-Regular.ttf"))
    }
}

/**
 * True when a [width] x [height] widget at [position] lies inside its page and overlaps no existing annotation
 * (DSS rejects overlapping signature fields, and a stamp over another signature would hide it).
 */
fun stampFits(doc: PDDocument, position: StampPosition, width: Float, height: Float): Boolean {
    if (position.page !in 1..doc.numberOfPages) return false
    val page = doc.getPage(position.page - 1)
    val box = page.cropBox
    if (position.x < 0f || position.y < 0f || position.x + width > box.width || position.y + height > box.height) return false
    val left = box.lowerLeftX + position.x
    val top = box.upperRightY - position.y
    return page.annotations.mapNotNull { it.rectangle }.none { r ->
        r.lowerLeftX < left + width + 0.5f && r.upperRightX > left - 0.5f &&
            r.lowerLeftY < top + 0.5f && r.upperRightY > top - height - 0.5f
    }
}

/**
 * Where a visible stamp goes unless the user chose: the first free spot from the bottom-right corner of the
 * last page, walking left, then up.
 */
fun defaultStampPosition(doc: PDDocument, width: Float, height: Float): StampPosition {
    val pageNumber = doc.numberOfPages
    val box = doc.getPage(pageNumber - 1).cropBox
    val columns = ((box.width - 2 * MARGIN + GAP) / (width + GAP)).toInt().coerceAtLeast(1)
    val rows = ((box.height - 2 * MARGIN + GAP) / (height + GAP)).toInt().coerceAtLeast(1)
    val candidates = (0 until rows).asSequence().flatMap { row ->
        (0 until columns).asSequence().map { column ->
            StampPosition(
                pageNumber,
                box.width - MARGIN - width - column * (width + GAP),
                box.height - MARGIN - height - row * (height + GAP),
            )
        }
    }
    return candidates.firstOrNull { stampFits(doc, it, width, height) } ?: candidates.first()
}

private const val MARGIN = 24f
private const val GAP = 6f
