package sh.yetkin.esigner.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateTest {
    private fun newState() = AppState(CoroutineScope(Dispatchers.Unconfined), persistSettings = false)

    /** Closes [state] while its card thread hangs; returns how long dispose took, in milliseconds. */
    private fun closeTimeWithHungCard(state: AppState): Long {
        val hung = CountDownLatch(1)
        // Stands in for a PKCS#11 call that never returns, like C_GetSlotList after the card is pulled.
        state.scanScope.launch { hung.await() }
        try {
            val start = System.nanoTime()
            val closing = thread { state.dispose() }
            closing.join(20_000)
            assertFalse(closing.isAlive, "dispose must give up on the card thread after its timeout")
            return (System.nanoTime() - start) / 1_000_000
        } finally {
            hung.countDown()
        }
    }

    @Test
    fun `closing while idle does not wait on a hung card thread`() {
        assertTrue(closeTimeWithHungCard(newState()) < 2_000, "nothing to close, no wait")
    }

    @Test
    fun `closing during a login gives up on a hung card thread`() {
        closeTimeWithHungCard(newState().apply { flow = SignFlow.Confirm(verifying = true) })
    }
}
