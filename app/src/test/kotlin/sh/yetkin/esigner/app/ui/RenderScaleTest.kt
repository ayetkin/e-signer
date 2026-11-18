package sh.yetkin.esigner.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RenderScaleTest {
    @Test
    fun `render scale rounds up, keeps a floor and caps the image width`() {
        assertEquals(1.25f, renderScale(700f, 595f))
        assertEquals(0.5f, renderScale(100f, 595f), "small views still render at half scale")
        assertEquals(2560f / 1000f, renderScale(4000f, 1000f), "deep zoom stays at 2560 px")
        // A 72 inch plotter sheet: the cap is below the floor and wins instead of throwing.
        assertEquals(2560f / 5184f, renderScale(800f, 5184f))
    }
}
