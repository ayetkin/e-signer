package sh.yetkin.esigner.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import sh.yetkin.esigner.app.i18n.Language
import sh.yetkin.esigner.app.ui.App
import sh.yetkin.esigner.app.ui.AppTheme
import sh.yetkin.esigner.core.CardScan
import sh.yetkin.esigner.core.CertificateInfo
import sh.yetkin.esigner.core.DetectedCard
import sh.yetkin.esigner.core.PinStatus
import sh.yetkin.esigner.core.SigningError
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test

/**
 * Renders every screen with sample data to app/build/screenshots, for reviewing the UI without a card
 * or a display. Run with -PcomposeTarget matching the machine running the tests (linux-arm64 in the container).
 */
class ScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }
    private val docs = File("build/sample-docs").apply { mkdirs() }

    private val holder = CertificateInfo(
        commonName = "JANE DOE",
        maskedNationalId = "12*******01",
        issuerName = "Sample Qualified Certificate Authority",
        notBefore = Instant.now().minus(200, ChronoUnit.DAYS),
        notAfter = Instant.now().plus(800, ChronoUnit.DAYS),
        isSigningCertificate = true,
    )
    private val card = DetectedCard(1, "ACS ACR39U ICC Reader", "AKIS", "AKIS v2.6", PinStatus.OK, holder)

    private fun sampleFiles() = listOf(
        "Service Agreement 2026.pdf" to 482_000,
        "Board decision.pdf" to 131_000,
        "Invoice-1042.xml" to 9_400,
        "Technical specification v3.docx" to 1_850_000,
    ).map { (name, size) -> File(docs, name).apply { writeBytes(ByteArray(size)) } }

    private fun state(language: Language, setup: AppState.() -> Unit): AppState =
        AppState(CoroutineScope(Dispatchers.Unconfined)).apply {
            switchLanguageForTest(language)
            setup()
        }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun shot(name: String, state: AppState, dark: Boolean = false) {
        val scene = ImageComposeScene(width = 1040 * 2, height = 720 * 2, density = Density(2f)) {
            AppTheme(dark = dark) { App(state, pickFiles = {}) }
        }
        scene.render(0)
        val image = scene.render(500_000_000)
        File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Test
    fun screens() {
        for (lang in Language.entries) {
            val l = lang.tag
            shot("$l-01-card-searching", state(lang) { scan = ScanState.Searching })
            shot("$l-02-card-no-reader", state(lang) { scan = ScanState.Ready(CardScan(0, emptyList())) })
            shot("$l-03-card-found", state(lang) {
                scan = ScanState.Ready(CardScan(1, listOf(card)))
                selectedSlot = 1
            })
            shot("$l-04-card-two-final-try", state(lang) {
                scan = ScanState.Ready(
                    CardScan(2, listOf(card, card.copy(slotId = 2, readerName = "Reader 2", pinStatus = PinStatus.FINAL_TRY))),
                )
                selectedSlot = 2
            })
            shot("$l-05-card-failed", state(lang) {
                scan = ScanState.Failed(UiError(SigningError.LIBRARY_NOT_FOUND, "/usr/local/lib/libakisp11.dylib"))
            })
            val ready: AppState.() -> Unit = {
                scan = ScanState.Ready(CardScan(1, listOf(card)))
                selectedSlot = 1
                screen = Screen.WORKSPACE
            }
            shot("$l-10-workspace-empty", state(lang, ready))
            shot("$l-11-workspace-files", state(lang) { ready(); files.addAll(sampleFiles()) })
            shot("$l-12-dialog", state(lang) { ready(); files.addAll(sampleFiles()); flow = SignFlow.Confirm() })
            shot("$l-13-dialog-wrong-pin", state(lang) {
                ready()
                files.addAll(sampleFiles())
                scan = ScanState.Ready(CardScan(1, listOf(card.copy(pinStatus = PinStatus.COUNT_LOW))))
                flow = SignFlow.Confirm(error = UiError(SigningError.PIN_INCORRECT, detail = "CKR_PIN_INCORRECT"))
            })
            shot("$l-14-signing", state(lang) {
                ready()
                files.addAll(sampleFiles())
                flow = SignFlow.Signing(1, 4, "Board decision.pdf")
            })
            shot("$l-15-results", state(lang) {
                ready()
                val f = sampleFiles()
                files.addAll(f)
                flow = SignFlow.Finished(
                    listOf(
                        FileResult(f[0], File(docs, "Service Agreement 2026-signed.pdf")),
                        FileResult(f[1], File(docs, "Board decision-signed.pdf")),
                        FileResult(f[2], null, UiError(SigningError.OUTPUT_EXISTS, "Invoice-1042.xml.imz")),
                        FileResult(f[3], File(docs, "Technical specification v3.docx.imz")),
                    ),
                )
            })
            shot("$l-16-card-removed", state(lang) {
                ready()
                files.addAll(sampleFiles())
                scan = ScanState.Ready(CardScan(1, emptyList()))
            })
        }
        shot("dark-11-workspace-files", state(Language.EN) {
            scan = ScanState.Ready(CardScan(1, listOf(card)))
            selectedSlot = 1
            screen = Screen.WORKSPACE
            files.addAll(sampleFiles())
        }, dark = true)
        shot("dark-03-card-found", state(Language.EN) {
            scan = ScanState.Ready(CardScan(1, listOf(card)))
            selectedSlot = 1
        }, dark = true)
    }
}
