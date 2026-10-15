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
import sh.yetkin.esigner.core.IdType
import sh.yetkin.esigner.core.PinStatus
import sh.yetkin.esigner.core.SignatureCheck
import sh.yetkin.esigner.core.ValidationReport
import sh.yetkin.esigner.core.Verdict
import sh.yetkin.esigner.core.SigningError
import sh.yetkin.esigner.core.SigningLevel
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import sh.yetkin.esigner.core.StampPosition
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

    /** A company's fiscal seal card: tax id, no nonRepudiation. */
    private val sealCard = card.copy(
        label = "MALI MUHUR",
        holder = holder.copy(
            commonName = "SAMPLE COMPANY INC",
            maskedNationalId = "12******90",
            idType = IdType.TAX_ID,
            isSigningCertificate = false,
            isFiscalSeal = true,
        ),
    )

    private fun sampleFiles() = listOf(
        "Service Agreement 2026.pdf" to 482_000,
        "Board decision.pdf" to 131_000,
        "Invoice-1042.xml" to 9_400,
        "Technical specification v3.docx" to 1_850_000,
    ).map { (name, size) -> File(docs, name).apply { writeBytes(ByteArray(size)) } }
        .also { samplePdf(it.first()) }

    /**
     * A one-page contract laid out like a real one: title, parties, numbered sections and a signature block at the
     * bottom with a place for each party. [SIGNATURE_SPOT] is the empty space above the client's signature line.
     */
    private fun samplePdf(file: File) = PDDocument().use { doc ->
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val regular = PDType1Font.HELVETICA
        val bold = PDType1Font.HELVETICA_BOLD
        val left = 64f
        val width = PDRectangle.A4.width - 2 * left
        PDPageContentStream(doc, page).use { content ->
            var y = 770f
            fun text(value: String, font: PDType1Font, size: Float, x: Float = left) {
                content.beginText()
                content.setFont(font, size)
                content.newLineAtOffset(x, y)
                content.showText(value)
                content.endText()
            }
            fun paragraph(value: String) {
                val lines = mutableListOf("")
                value.split(" ").forEach { word ->
                    val candidate = if (lines.last().isEmpty()) word else lines.last() + " " + word
                    if (regular.getStringWidth(candidate) / 1000 * 10.5f > width) lines += word else lines[lines.lastIndex] = candidate
                }
                lines.forEach { text(it, regular, 10.5f); y -= 15f }
            }
            val title = "SERVICE AGREEMENT"
            text(title, bold, 16f, (PDRectangle.A4.width - bold.getStringWidth(title) / 1000 * 16f) / 2)
            y -= 34f
            paragraph(
                "This Service Agreement is made on 1 October 2026 between Example Provider Ltd., Istanbul (the \"Provider\") " +
                    "and Sample Company Inc., Ankara (the \"Client\").",
            )
            listOf(
                "1. Services" to "The Provider will set up, operate and support the document management system described in " +
                    "Annex A, including updates, backups and help desk support on business days.",
                "2. Term" to "This agreement starts on 1 November 2026 and runs for twelve months. It renews for another " +
                    "twelve months unless either party gives written notice at least thirty days before the end of the term.",
                "3. Fees and payment" to "The Client will pay a monthly fee of 12,500 TRY plus VAT. The Provider invoices at " +
                    "the start of each month and invoices are due within fifteen days.",
                "4. Confidentiality" to "Each party keeps the other party's confidential information secret and uses it only " +
                    "to perform this agreement. This obligation continues for three years after the agreement ends.",
                "5. Governing law" to "This agreement is governed by the laws of the Republic of Turkey. The courts of " +
                    "Istanbul have jurisdiction over any dispute.",
            ).forEach { (heading, body) ->
                y -= 10f
                text(heading, bold, 11f)
                y -= 16f
                paragraph(body)
            }
            // Signature block: one column per party, the line at the bottom of the empty signature space.
            y = 842f - SIGNATURE_LINE_FROM_TOP
            listOf(left to "For the Provider", left + width / 2 + 10f to "For the Client").forEachIndexed { i, (x, label) ->
                val saved = y
                y = 842f - SIGNATURE_SPOT.y + 18f
                text(label, bold, 10.5f, x)
                y = saved
                content.moveTo(x, y)
                content.lineTo(x + width / 2 - 10f, y)
                content.stroke()
                y -= 14f
                text(if (i == 0) "Name: John Smith" else "Name: Jane Doe", regular, 10f, x)
                y -= 13f
                text("Date:", regular, 10f, x)
                y = saved
            }
        }
        doc.save(file)
    }

    private fun state(language: Language, setup: AppState.() -> Unit): AppState =
        AppState(CoroutineScope(Dispatchers.Unconfined), persistSettings = false).apply {
            switchLanguageForTest(language)
            setup()
        }

    @OptIn(ExperimentalComposeUiApi::class)
    // A maximized 14-inch MacBook Pro window by default: 1512 x 982 points at 2x (Retina), so 3024 x 1964 pixels.
    // The card screen has its own smaller window, as in the app.
    private fun shot(name: String, state: AppState, dark: Boolean = false, width: Int? = null, height: Int? = null) {
        val card = state.screen == Screen.CARD_SELECT
        val width = width ?: if (card) SigningSettings.CARD_WINDOW_SIZE.width.value.toInt() else 1512
        val height = height ?: if (card) SigningSettings.CARD_WINDOW_SIZE.height.value.toInt() else 982
        val scene = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) {
            AppTheme(dark = dark) { App(state, pickFiles = {}) }
        }
        // A few frames with real time in between: the PDF preview loads and renders on background threads.
        repeat(3) {
            scene.render(it * 250_000_000L)
            Thread.sleep(150)
        }
        val image = scene.render(1_000_000_000)
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
            shot("$l-03b-card-fiscal-seal", state(lang) {
                scan = ScanState.Ready(CardScan(1, listOf(sealCard)))
                selectedSlot = 1
            })
            shot("$l-12b-dialog-fiscal-seal", state(lang) {
                ready()
                scan = ScanState.Ready(CardScan(1, listOf(sealCard)))
                files.addAll(sampleFiles())
                flow = SignFlow.Confirm()
            })
            shot("$l-18d-settings-card-fiscal-seal", state(lang) {
                ready()
                scan = ScanState.Ready(CardScan(1, listOf(sealCard)))
                openSettings(SettingsSection.CARD)
            })
            shot("$l-10-workspace-empty", state(lang, ready))
            shot("$l-11-workspace-files", state(lang) { ready(); files.addAll(sampleFiles()) })
            shot("$l-11b-workspace-stamp", state(lang) {
                ready()
                files.addAll(sampleFiles())
                settings.updateStamp(StampStyle.ICON_TEXT)
                placeStamp(files.first(), SIGNATURE_SPOT)
            })
            // The smallest window: the preview goes under the list.
            shot("$l-11c-workspace-narrow", state(lang) {
                ready()
                files.addAll(sampleFiles())
                settings.updateStamp(StampStyle.TEXT)
            }, width = 880)
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
            val kamuSm: AppState.() -> Unit = {
                settings.setForTest(SigningLevel.BASELINE_T, SigningSettings.KAMU_SM_URL, TsaAuthType.KAMU_SM, "12345", "secret")
            }
            shot("$l-17-settings-kamusm", state(lang) { ready(); files.addAll(sampleFiles()); kamuSm(); openSettings(SettingsSection.TIMESTAMP) })
            shot("$l-17b-settings-kamusm-no-auth", state(lang) {
                ready()
                settings.setForTest(SigningLevel.BASELINE_T, SigningSettings.KAMU_SM_URL, TsaAuthType.BASIC)
                openSettings(SettingsSection.TIMESTAMP)
            })
            shot("$l-18-settings-signature", state(lang) {
                ready()
                files.addAll(sampleFiles())
                settings.updatePdfDetails(defaultReason, "Istanbul", defaultReason)
                openSettings(SettingsSection.SIGNATURE)
            })
            shot("$l-18b-settings-general", state(lang) { ready(); openSettings() })
            shot("$l-18c-settings-card", state(lang) { ready(); openSettings(SettingsSection.CARD) })
            shot("$l-18e-settings-on-card-screen", state(lang) { openSettings(SettingsSection.CARD) })
            shot("$l-22-about", state(lang) { ready(); openAbout() })
            shot("$l-22b-about-update", state(lang) { ready(); openAbout(); updateCheck = UpdateCheck.Available("0.5.0") })
            shot("$l-19-workspace-tsa-missing", state(lang) {
                ready()
                files.addAll(sampleFiles())
                settings.setForTest(SigningLevel.BASELINE_LT)
            })
            shot("$l-11d-workspace-timestamped", state(lang) {
                ready()
                files.addAll(sampleFiles())
                kamuSm()
                settings.updateStamp(StampStyle.ICON_TEXT)
                placeStamp(files.first(), SIGNATURE_SPOT)
            })
            shot("$l-1a-dialog-timestamped", state(lang) { ready(); files.addAll(sampleFiles()); kamuSm(); flow = SignFlow.Confirm() })
            shot("$l-1b-results-tsa-rejected", state(lang) {
                ready()
                kamuSm()
                val f = sampleFiles()
                files.addAll(f)
                flow = SignFlow.Finished(
                    listOf(
                        FileResult(
                            f[0],
                            null,
                            UiError(
                                SigningError.TIMESTAMP_REJECTED,
                                "HTTP 401",
                                "TIMESTAMP_REJECTED: HTTP 401\n" +
                                    "Caused by: sh.yetkin.esigner.core.TsaResponseError: POST http://zd.kamusm.gov.tr -> HTTP 401\n" +
                                    "Content-Type: text/plain, 12 bytes\nUnauthorized",
                            ),
                        ),
                        FileResult(f[1], null, skipped = true),
                        FileResult(f[2], null, skipped = true),
                        FileResult(f[3], null, skipped = true),
                    ),
                )
            })
            shot("$l-16-card-removed", state(lang) {
                ready()
                files.addAll(sampleFiles())
                scan = ScanState.Ready(CardScan(1, emptyList()))
            })
        }
        for (lang in Language.entries) {
            val l = lang.tag
            shot("$l-20-verify-empty", state(lang) { screen = Screen.WORKSPACE; tab = Tab.VERIFY })
            shot("$l-21-verify-results", state(lang) {
                screen = Screen.WORKSPACE
                tab = Tab.VERIFY
                val f = sampleFiles()
                val legal = SignatureCheck(
                    signer = holder.copy(isQualified5070 = true),
                    verdict = Verdict.VALID,
                    subIndication = null,
                    trustedChain = true,
                    claimedSigningTime = Instant.parse("2026-10-14T09:30:00Z"),
                    timestampTime = null,
                    format = "PAdES-BASELINE-B",
                    messages = listOf("The signature does not contain a timestamp."),
                )
                val seal = legal.copy(
                    signer = holder.copy(
                        commonName = "SAMPLE COMPANY INC",
                        maskedNationalId = "12******90",
                        idType = IdType.TAX_ID,
                        isFiscalSeal = true,
                        isQualified5070 = false,
                    ),
                )
                verifyItems.addAll(
                    listOf(
                        VerifyItem(
                            f[0],
                            VerifyState.Done(
                                ValidationReport(
                                    f[0],
                                    listOf(
                                        seal,
                                        legal.copy(
                                            timestampTime = Instant.parse("2026-10-14T09:30:04Z"),
                                            timestampAuthority = "Sample Timestamp Authority",
                                            trustedTimestamp = true,
                                            format = "PAdES-BASELINE-T",
                                            messages = emptyList(),
                                        ),
                                    ),
                                    false,
                                ),
                            ),
                        ),
                        VerifyItem(
                            f[1],
                            VerifyState.Done(
                                ValidationReport(
                                    f[1],
                                    listOf(legal.copy(verdict = Verdict.INVALID, subIndication = "HASH_FAILURE")),
                                    false,
                                ),
                            ),
                        ),
                        VerifyItem(f[2], VerifyState.Running),
                        VerifyItem(
                            f[3],
                            VerifyState.Done(
                                ValidationReport(
                                    f[3],
                                    listOf(legal.copy(verdict = Verdict.INDETERMINATE, subIndication = "TRY_LATER")),
                                    true,
                                ),
                            ),
                        ),
                    ),
                )
            })
        }
        shot("dark-11-workspace-files", state(Language.EN) {
            scan = ScanState.Ready(CardScan(1, listOf(card)))
            selectedSlot = 1
            screen = Screen.WORKSPACE
            files.addAll(sampleFiles())
        }, dark = true)
        shot("dark-17-settings-kamusm", state(Language.EN) {
            scan = ScanState.Ready(CardScan(1, listOf(card)))
            selectedSlot = 1
            screen = Screen.WORKSPACE
            files.addAll(sampleFiles())
            settings.setForTest(SigningLevel.BASELINE_LT, SigningSettings.KAMU_SM_URL, TsaAuthType.KAMU_SM, "12345", "secret")
            openSettings(SettingsSection.TIMESTAMP)
        }, dark = true)
        shot("dark-03-card-found", state(Language.EN) {
            scan = ScanState.Ready(CardScan(1, listOf(card)))
            selectedSlot = 1
        }, dark = true)
    }
}

/** Top-left corner of the space above the client's signature line in the sample contract, in PDF points from the top. */
private val SIGNATURE_SPOT = StampPosition(1, 309.5f, 470f)
private const val SIGNATURE_LINE_FROM_TOP = 520f
