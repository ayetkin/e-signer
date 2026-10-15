package sh.yetkin.esigner.core

import eu.europa.esig.dss.pades.DSSFileFont
import org.apache.pdfbox.pdmodel.PDDocument
import java.awt.font.FontRenderContext
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
     * that makes the lines fill the height, so DSS's FILL_BOX layout adds no space of its own.
     */
    fun size(signerName: String): Pair<Float, Float> {
        val textWidth = text(signerName, Instant.now())?.let(::fittedWidth) ?: 0f
        return imageWidth + textWidth to height
    }

    /** Font size in points at which the label's lines fill the height, as DSS draws them. */
    fun fontSize(signerName: String): Float = text(signerName, Instant.now())?.let { SAMPLE_SIZE * fillScale(it) } ?: 0f

    private fun fittedWidth(text: String): Float {
        val sample = font.deriveFont(SAMPLE_SIZE)
        val context = FontRenderContext(null, true, true)
        // Every digit as the widest one, so the box fits the signing time whatever it turns out to be.
        val widest = text.lines().maxOf { sample.getStringBounds(it.replace(DIGIT, widestDigit), context).width }.toFloat()
        return widest * fillScale(text) + 2 * padding + height * RIGHT_RATIO
    }

    /** How much the sample font size must grow for the lines to fill the height inside the padding. */
    private fun fillScale(text: String): Float {
        val lineHeight = font.deriveFont(SAMPLE_SIZE).getLineMetrics(text, FontRenderContext(null, true, true)).height
        return (height - 2 * padding) / (lineHeight * text.lines().size)
    }

    companion object {
        const val DEFAULT_HEIGHT = 35f

        const val MIN_HEIGHT = 16f
        const val MAX_HEIGHT = 80f

        /** Margin to the frame, icon side and gap to the text next to it, as fractions of the height. */
        private const val EDGE_RATIO = 0.08f
        private const val ICON_RATIO = 0.8f
        private const val GAP_RATIO = 0.12f

        /** Extra space between the text and the right edge of the frame, as a fraction of the height. */
        private const val RIGHT_RATIO = 0.08f
        private const val SAMPLE_SIZE = 10f
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

        /** DSS's default font, the one it draws the text with. */
        private val font by lazy { DSSFileFont.initializeDefault().javaFont }

        private val DIGIT = Regex("[0-9]")
        private val widestDigit by lazy {
            val sample = font.deriveFont(SAMPLE_SIZE)
            val context = FontRenderContext(null, true, true)
            ('0'..'9').maxBy { sample.getStringBounds(it.toString(), context).width }.toString()
        }

        /** That font's file, so a preview can draw the text exactly as the signed PDF will. */
        val fontData: ByteArray by lazy { DSSFileFont.initializeDefault().inputStream.use { it.readBytes() } }
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
