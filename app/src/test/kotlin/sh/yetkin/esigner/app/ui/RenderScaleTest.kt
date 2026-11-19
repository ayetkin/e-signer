package sh.yetkin.esigner.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderScaleTest {
    private val a4Pixels = 2560f * 2560f * 842f / 595f

    @Test
    fun `render scale rounds up, keeps a floor and caps the image`() {
        assertEquals(1.25f, renderScale(700f, 595f, 842f))
        assertEquals(0.5f, renderScale(100f, 595f, 842f), "small views still render at half scale")
        assertEquals(2560f / 1000f, renderScale(4000f, 1000f, 1000f), "deep zoom stays at 2560 px")
        // A 72 inch plotter sheet: the width cap is below the floor and wins instead of throwing.
        assertEquals(2560f / 5184f, renderScale(800f, 5184f, 3600f))
        // A one-page browser export 200 inches tall: capped at the pixels of an A4 page 2560 px wide.
        val tall = renderScale(1600f, 595f, 14400f)
        assertTrue(tall * 595f * tall * 14400f <= a4Pixels * 1.001f, "scale $tall")
        assertTrue(tall > 1f, "still sharper than the floor: $tall")
    }
}
