package sh.yetkin.esigner.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sh.yetkin.esigner.app.i18n.Language
import sh.yetkin.esigner.app.i18n.Strings
import sh.yetkin.esigner.core.CardScan
import sh.yetkin.esigner.core.CardScanner
import sh.yetkin.esigner.core.CardSession
import sh.yetkin.esigner.core.DetectedCard
import sh.yetkin.esigner.core.DocumentSigner
import sh.yetkin.esigner.core.FormatMode
import sh.yetkin.esigner.core.PinStatus
import sh.yetkin.esigner.core.SigningError
import sh.yetkin.esigner.core.SignatureValidator
import sh.yetkin.esigner.core.SigningException
import sh.yetkin.esigner.core.StampPosition
import sh.yetkin.esigner.core.ValidationReport
import sh.yetkin.esigner.core.VisibleStamp
import sh.yetkin.esigner.core.formatFor
import sh.yetkin.esigner.core.outputFileFor
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.Executors

enum class Screen { CARD_SELECT, WORKSPACE }

enum class Tab { SIGN, VERIFY }

enum class SettingsSection { GENERAL, SIGNATURE, TIMESTAMP, CARD }

sealed interface VerifyState {
    data object Running : VerifyState
    data class Done(val report: ValidationReport) : VerifyState
    data class Failed(val error: UiError) : VerifyState
}

data class VerifyItem(val file: File, val state: VerifyState)

sealed interface ScanState {
    data object Searching : ScanState
    data class Ready(val scan: CardScan) : ScanState
    data class Failed(val error: UiError) : ScanState
}

sealed interface SignFlow {
    data object Idle : SignFlow

    /** The confirmation dialog: PIN entry. [verifying] while the single login attempt runs. */
    data class Confirm(val error: UiError? = null, val verifying: Boolean = false) : SignFlow
    data class Signing(val done: Int, val total: Int, val current: String) : SignFlow
    data class Finished(val results: List<FileResult>) : SignFlow
}

/** [skipped]: not attempted because the card or the timestamp service failed on an earlier file. */
data class FileResult(val input: File, val output: File?, val error: UiError? = null, val skipped: Boolean = false)

/** A localizable error: the UI renders [error] with [subject]; [detail] is the technical cause chain. */
data class UiError(val error: SigningError, val subject: String? = null, val detail: String? = null) {
    companion object {
        fun of(e: SigningException) = UiError(e.error, e.subject, e.technicalDetail)
    }
}

/**
 * UI state and actions. Every card call (scan, login, sign) runs on one dedicated thread, since a
 * PKCS#11 module must not be used concurrently. A card session lives only for one signing run.
 */
class AppState(private val scope: CoroutineScope, persistSettings: Boolean = true) {
    private val cardThread = Executors.newSingleThreadExecutor { Thread(it, "card").apply { isDaemon = true } }
        .asCoroutineDispatcher()

    // Scans run in their own scope so a hung native call can be waited on with a timeout and reused, not queued again.
    private val scanScope = CoroutineScope(cardThread + SupervisorJob())
    private var pendingScan: Deferred<CardScan>? = null
    private var pendingLibrary: String? = null

    @Volatile
    private var activeSession: CardSession? = null
    private val validator by lazy { SignatureValidator() }

    var screen by mutableStateOf(Screen.CARD_SELECT)
        internal set
    var tab by mutableStateOf(Tab.SIGN)
        internal set
    val verifyItems = mutableStateListOf<VerifyItem>()
    var scan by mutableStateOf<ScanState>(ScanState.Searching)
        internal set
    var selectedSlot by mutableStateOf<Long?>(null)
        internal set
    var language by mutableStateOf(Language.initial())
        private set
    val strings: Strings get() = Strings.of(language)

    val settings = SigningSettings(persistSettings)
    var settingsOpen by mutableStateOf(false)
        internal set
    var settingsSection by mutableStateOf(SettingsSection.GENERAL)
        internal set

    val files = mutableStateListOf<File>()

    /** Where the user put the visible signature on each PDF; the others get the default spot. */
    val stampPositions = mutableStateMapOf<File, StampPosition>()

    /** The stamp box shows its handles; a click outside the preview clears it until the preview is clicked. */
    var stampFocused by mutableStateOf(true)
    var mode by mutableStateOf(FormatMode.AUTO)
    var flow by mutableStateOf<SignFlow>(SignFlow.Idle)
        internal set

    val cards: List<DetectedCard> get() = (scan as? ScanState.Ready)?.scan?.cards.orEmpty()

    /** The chosen card if it is still inserted. */
    val selectedCard: DetectedCard? get() = cards.firstOrNull { it.slotId == selectedSlot }

    val canSign: Boolean
        get() = files.isNotEmpty() && selectedCard?.let { it.pinStatus != PinStatus.LOCKED } == true &&
            // DSS refuses an expired certificate only after login; do not ask for the PIN in that case.
            selectedCard?.holder?.isValidAt(Instant.now()) != false &&
            flow == SignFlow.Idle && !settings.missingTsa

    /** What signed PDFs show, in the current language; null when signatures are invisible. */
    val visibleStamp: VisibleStamp?
        get() {
            val style = settings.stamp
            if (style == StampStyle.NONE) return null
            return VisibleStamp(strings["stamp.label"].takeIf { style.text }, stampIcon.takeIf { style.icon }, settings.stampHeight)
        }

    val signerName: String get() = selectedCard?.holder?.commonName.orEmpty()

    // Card detection

    /** Rescans every two seconds for the lifetime of the UI, except while a card session is in use. */
    suspend fun watchCards() {
        while (true) {
            if (flow !is SignFlow.Signing && (flow as? SignFlow.Confirm)?.verifying != true) rescan()
            delay(2_000)
        }
    }

    private suspend fun rescan() {
        val library = settings.libraryPath
        // A changed library path starts its own scan instead of waiting on one for the old path.
        val pending = pendingScan?.takeIf { it.isActive && pendingLibrary == library }
            ?: scanScope.async { CardScanner.scan(library) }.also {
                pendingScan = it
                pendingLibrary = library
            }
        scan = try {
            withTimeoutOrNull(SCAN_TIMEOUT_MS) { pending.await() }?.let { ScanState.Ready(it) }
                ?: ScanState.Failed(
                    UiError(
                        SigningError.CARD_NOT_RESPONDING,
                        detail = "CARD_NOT_RESPONDING\nPKCS#11 module $library did not answer a card scan within $SCAN_TIMEOUT_MS ms",
                    ),
                )
        } catch (e: SigningException) {
            ScanState.Failed(UiError.of(e))
        }
        // A single card is chosen automatically; a removed card is forgotten on the selection screen.
        if (screen == Screen.CARD_SELECT && selectedCard == null) {
            selectedSlot = cards.singleOrNull()?.slotId
        }
    }

    fun retryScan() {
        scan = ScanState.Searching
        scope.launch { rescan() }
    }

    fun selectCard(slotId: Long) {
        selectedSlot = slotId
    }

    fun continueWithCard() {
        if (selectedCard != null) {
            tab = Tab.SIGN
            screen = Screen.WORKSPACE
        }
    }

    /** Validation needs no card. */
    fun verifyWithoutCard() {
        tab = Tab.VERIFY
        screen = Screen.WORKSPACE
    }

    fun selectTab(value: Tab) {
        if (flow is SignFlow.Signing || flow is SignFlow.Confirm) return
        tab = value
        if (value == Tab.SIGN && selectedCard == null) screen = Screen.CARD_SELECT
    }

    fun changeCard() {
        if (flow is SignFlow.Signing) return
        flow = SignFlow.Idle
        screen = Screen.CARD_SELECT
    }

    // Validation

    fun addForVerify(newFiles: List<File>) {
        newFiles.map { it.absoluteFile }.filter { it.isFile && verifyItems.none { item -> item.file == it } }.forEach { file ->
            verifyItems.add(VerifyItem(file, VerifyState.Running))
            scope.launch {
                val result = try {
                    VerifyState.Done(withContext(Dispatchers.IO) { validator.validate(file) })
                } catch (e: SigningException) {
                    VerifyState.Failed(UiError.of(e))
                } catch (e: Exception) {
                    VerifyState.Failed(UiError(SigningError.UNEXPECTED, e.javaClass.simpleName))
                } catch (e: LinkageError) {
                    // One file's validation must never crash the app.
                    VerifyState.Failed(UiError(SigningError.UNEXPECTED, e.javaClass.simpleName))
                }
                val index = verifyItems.indexOfFirst { it.file == file }
                if (index >= 0) verifyItems[index] = VerifyItem(file, result)
            }
        }
    }

    fun removeVerify(file: File) {
        verifyItems.removeAll { it.file == file }
    }

    fun clearVerify() {
        verifyItems.clear()
    }

    /**
     * Saves the signed content of an .imz where the user chose. The save dialog already confirmed replacing an
     * existing file, so it is replaced, but only once the content is fully written. Returns an error to show, if any.
     */
    suspend fun extractContent(file: File, target: File): UiError? = withContext(Dispatchers.IO) {
        val temp = File(target.absoluteFile.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            validator.extractOriginal(file, temp)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            null
        } catch (e: SigningException) {
            UiError.of(e)
        } catch (e: java.io.IOException) {
            UiError(SigningError.WRITE_FAILED, target.name, e.toString())
        } finally {
            temp.delete()
        }
    }

    /** Extracts the signed content of an .imz to a temporary folder and opens it. */
    suspend fun openContent(file: File): Pair<File?, UiError?> = withContext(Dispatchers.IO) {
        val dir = Files.createTempDirectory("esigner").toFile().apply { deleteOnExit() }
        val target = File(dir, contentName(file)).apply { deleteOnExit() }
        try {
            validator.extractOriginal(file, target)
            target to null
        } catch (e: SigningException) {
            null to UiError.of(e)
        }
    }

    // Files

    fun addFiles(newFiles: List<File>) {
        if (flow != SignFlow.Idle) return
        files.addAll(newFiles.map { it.absoluteFile }.filter { it.isFile && it !in files })
    }

    fun removeFile(file: File) {
        if (flow != SignFlow.Idle) return
        files.remove(file)
        stampPositions.remove(file)
    }

    fun clearFiles() {
        if (flow != SignFlow.Idle) return
        files.clear()
        stampPositions.clear()
    }

    /** [position] null goes back to the default spot. */
    fun placeStamp(file: File, position: StampPosition?) {
        if (flow != SignFlow.Idle) return
        if (position == null) stampPositions.remove(file) else stampPositions[file] = position
    }

    // Signature settings

    fun openSettings(section: SettingsSection = SettingsSection.GENERAL) {
        if (flow != SignFlow.Idle) return
        settingsSection = section
        settingsOpen = true
    }

    fun closeSettings() {
        settingsOpen = false
    }

    // Signing

    fun requestSign() {
        if (canSign) flow = SignFlow.Confirm()
    }

    fun cancelSign() {
        if (flow is SignFlow.Confirm && !(flow as SignFlow.Confirm).verifying) flow = SignFlow.Idle
    }

    /**
     * One login attempt, then all files are signed and the session is closed. Takes ownership of [pin].
     * A failed login is shown in the dialog; nothing is retried, the user has to type the PIN again.
     */
    fun sign(pin: CharArray) {
        val card = selectedCard
        val confirm = flow as? SignFlow.Confirm
        if (card == null || confirm == null || confirm.verifying || pin.isEmpty()) {
            pin.fill('\u0000')
            return
        }
        flow = SignFlow.Confirm(verifying = true)
        val library = settings.libraryPath
        val padesSuffix = strings["output.padesSuffix"]
        val jobs = files.map { it to formatFor(it, mode) }
        val level = settings.level
        val stamp = visibleStamp
        val positions = stampPositions.toMap()
        // Its own copy of the TSA password, cleared when the run ends.
        val timestamp = settings.timestampFor()

        scope.launch {
            // A hung scan blocks the card thread; never queue the login behind it, it would wait forever.
            val busyScan = pendingScan?.takeIf { it.isActive }
            if (busyScan != null && withTimeoutOrNull(SCAN_TIMEOUT_MS) { busyScan.join() } == null) {
                pin.fill('\u0000')
                timestamp?.second?.clear()
                flow = SignFlow.Confirm(error = UiError(SigningError.CARD_NOT_RESPONDING))
                return@launch
            }
            val session = try {
                withContext(cardThread) { CardSession.open(library, card.slotId, pin) }
            } catch (e: SigningException) {
                timestamp?.second?.clear()
                flow = SignFlow.Confirm(error = UiError.of(e))
                rescan() // refreshes the remaining-attempts warning
                return@launch
            }
            activeSession = session
            val results = mutableListOf<FileResult>()
            try {
                val key = session.keys[session.preferredKeyIndex]
                val signer = DocumentSigner(level, timestamp?.first, stamp = stamp)
                var batchStopped = false
                for ((index, job) in jobs.withIndex()) {
                    val (input, format) = job
                    if (batchStopped) {
                        results += FileResult(input, null, skipped = true)
                        continue
                    }
                    flow = SignFlow.Signing(index, jobs.size, input.name)
                    results += try {
                        val output = outputFileFor(input, format, padesSuffix)
                        FileResult(input, withContext(cardThread) { signer.sign(input, format, session.connection, key.entry, output, positions[input]) })
                    } catch (e: SigningException) {
                        batchStopped = e.stopsBatch
                        FileResult(input, null, UiError.of(e))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        FileResult(input, null, UiError(SigningError.UNEXPECTED, e.javaClass.simpleName))
                    }
                }
            } finally {
                // Also on an Error or cancellation: the session must close and the PIN be zeroed.
                withContext(NonCancellable + cardThread) { closeActiveSession() }
                timestamp?.second?.clear()
            }
            flow = SignFlow.Finished(results)
        }
    }

    fun switchLanguage(value: Language) {
        language = value
        value.save()
    }

    internal fun switchLanguageForTest(value: Language) {
        language = value
    }

    fun startOver() {
        files.clear()
        stampPositions.clear()
        flow = SignFlow.Idle
    }

    /**
     * Called on window close. A running signing step finishes first; the session closes and zeroes the PIN.
     * A card call that never returns (a hung module) must not keep the app from quitting, so the wait is bounded.
     */
    fun dispose() {
        // The signing coroutine may never resume before exit, so the session is closed here, not in its finally.
        runBlocking { withTimeoutOrNull(SCAN_TIMEOUT_MS) { withContext(cardThread) { closeActiveSession() } } }
        cardThread.close()
    }

    /** Closes the open session once, whoever comes first: the signing run or [dispose]. Card thread only. */
    private fun closeActiveSession() {
        activeSession?.also { activeSession = null }?.close()
    }
}

internal val stampIcon: ByteArray by lazy { AppState::class.java.getResourceAsStream("/stamp-icon.png")!!.use { it.readBytes() } }

/** A card scan normally takes well under a second; past this the module is considered hung. */
private const val SCAN_TIMEOUT_MS = 10_000L

/** "report.pdf.imz" -> "report.pdf"; anything else gets "-content" appended to its name. */
fun contentName(file: File): String =
    if (file.extension.equals("imz", ignoreCase = true)) file.nameWithoutExtension else file.name + "-content"
