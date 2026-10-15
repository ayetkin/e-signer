package sh.yetkin.esigner.app.ui

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomScrollTest {
    private val ratios = listOf(0.7f, 0.7f, 1.4f)
    private val pageWidth = 700f
    private val gap = 12f
    private val pad = 16f
    private val viewWidth = 732f

    /** Document position (page, fraction across, fraction down) under [anchor] for the given scroll and zoom. */
    private fun pointUnder(zoom: Float, scrollX: Float, scrollY: Float, anchor: Offset): Triple<Int, Float, Float> {
        val width = pageWidth * zoom
        val left = (maxOf(viewWidth, width + 2 * pad) - width) / 2
        var docY = scrollY + anchor.y - pad
        ratios.forEachIndexed { i, ratio ->
            val height = width / ratio
            if (docY < height + gap) return Triple(i, (scrollX + anchor.x - left) / width, docY / height)
            docY -= height + gap
        }
        error("outside")
    }

    @Test
    fun `the point under the pointer stays in place`() {
        val anchor = Offset(500f, 300f)
        val (scrollX, scrollY) = 0f to scrollTop(ratios, pageWidth, gap, 1, 200)
        val before = pointUnder(1f, scrollX, scrollY, anchor)
        val (x, y) = zoomScroll(ratios, pageWidth, gap, pad, viewWidth, 1f, 2f, scrollX, scrollY, anchor)
        val after = pointUnder(2f, x, y, anchor)
        assertEquals(before.first, after.first)
        assertEquals(before.second, after.second, 0.001f)
        assertEquals(before.third, after.third, 0.001f)
    }

    @Test
    fun `scroll top and item position round trip`() {
        val top = scrollTop(ratios, pageWidth, gap, 2, 37)
        assertEquals(2 to 37, itemAt(ratios, pageWidth, gap, top))
    }
}
