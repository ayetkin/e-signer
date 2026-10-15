package sh.yetkin.esigner.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatesTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updates.isNewer("0.10.0", "0.9.3"))
        assertTrue(Updates.isNewer("0.4.2", "0.4.1"))
        assertTrue(Updates.isNewer("1.0", "0.4.1"))
        assertFalse(Updates.isNewer("0.4.1", "0.4.1"))
        assertFalse(Updates.isNewer("0.4.0", "0.4.1"))
        assertFalse(Updates.isNewer("0.4.1-rc1", "0.4.1"))
    }
}
