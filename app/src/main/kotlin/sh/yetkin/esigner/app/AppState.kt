package sh.yetkin.esigner.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import sh.yetkin.esigner.app.i18n.Language
import sh.yetkin.esigner.app.i18n.Strings
import sh.yetkin.esigner.core.CardScan
import sh.yetkin.esigner.core.CardScanner
import sh.yetkin.esigner.core.CardSession
import sh.yetkin.esigner.core.DetectedCard
import sh.yetkin.esigner.core.DocumentSigner
import sh.yetkin.esigner.core.FormatMode
import sh.yetkin.esigner.core.PinStatus
import sh.yetkin.esigner.core.Pkcs11Library
import sh.yetkin.esigner.core.SigningError
import sh.yetkin.esigner.core.SigningException
import sh.yetkin.esigner.core.formatFor
import sh.yetkin.esigner.core.outputFileFor
import java.io.File
import java.util.concurrent.Executors

enum class Screen { CARD_SELECT, WORKSPACE }

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

/** [skipped]: not attempted because the card failed on an earlier file. */
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
class AppState(private val scope: CoroutineScope) {
    private val cardThread = Executors.newSingleThreadExecutor { Thread(it, "card").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    private val signer = DocumentSigner()

    var screen by mutableStateOf(Screen.CARD_SELECT)
        internal set
    var scan by mutableStateOf<ScanState>(ScanState.Searching)
        internal set
    var selectedSlot by mutableStateOf<Long?>(null)
        internal set
    var libraryPath by mutableStateOf(Pkcs11Library.defaultPath())
    var language by mutableStateOf(Language.initial())
        private set
    val strings: Strings get() = Strings.of(language)

    val files = mutableStateListOf<File>()
    var mode by mutableStateOf(FormatMode.AUTO)
    var flow by mutableStateOf<SignFlow>(SignFlow.Idle)
        internal set

    val cards: List<DetectedCard> get() = (scan as? ScanState.Ready)?.scan?.cards.orEmpty()

    /** The chosen card if it is still inserted. */
    val selectedCard: DetectedCard? get() = cards.firstOrNull { it.slotId == selectedSlot }

    val canSign: Boolean
        get() = files.isNotEmpty() && selectedCard?.let { it.pinStatus != PinStatus.LOCKED } == true &&
            flow == SignFlow.Idle

    // Card detection

    /** Rescans every two seconds for the lifetime of the UI, except while a card session is in use. */
    suspend fun watchCards() {
        while (true) {
            if (flow !is SignFlow.Signing && (flow as? SignFlow.Confirm)?.verifying != true) rescan()
            delay(2_000)
        }
    }

    private suspend fun rescan() {
        val library = libraryPath.trim()
        scan = try {
            ScanState.Ready(withContext(cardThread) { CardScanner.scan(library) })
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
        if (selectedCard != null) screen = Screen.WORKSPACE
    }

    fun changeCard() {
        if (flow is SignFlow.Signing) return
        flow = SignFlow.Idle
        screen = Screen.CARD_SELECT
    }

    // Files

    fun addFiles(newFiles: List<File>) {
        if (flow != SignFlow.Idle) return
        files.addAll(newFiles.map { it.absoluteFile }.filter { it.isFile && it !in files })
    }

    fun removeFile(file: File) {
        if (flow == SignFlow.Idle) files.remove(file)
    }

    fun clearFiles() {
        if (flow == SignFlow.Idle) files.clear()
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
        val library = libraryPath.trim()
        val padesSuffix = strings["output.padesSuffix"]
        val jobs = files.map { it to formatFor(it, mode) }

        scope.launch {
            val session = try {
                withContext(cardThread) { CardSession.open(library, card.slotId, pin) }
            } catch (e: SigningException) {
                flow = SignFlow.Confirm(error = UiError.of(e))
                rescan() // refreshes the remaining-attempts warning
                return@launch
            }
            val key = session.keys[session.preferredKeyIndex]
            val results = mutableListOf<FileResult>()
            var cardFailure = false
            for ((index, job) in jobs.withIndex()) {
                val (input, format) = job
                if (cardFailure) {
                    results += FileResult(input, null, skipped = true)
                    continue
                }
                flow = SignFlow.Signing(index, jobs.size, input.name)
                results += try {
                    val output = outputFileFor(input, format, padesSuffix)
                    FileResult(input, withContext(cardThread) { signer.sign(input, format, session.connection, key.entry, output) })
                } catch (e: SigningException) {
                    cardFailure = e.isCardError
                    FileResult(input, null, UiError.of(e))
                } catch (e: Exception) {
                    FileResult(input, null, UiError(SigningError.UNEXPECTED, e.javaClass.simpleName))
                }
            }
            withContext(cardThread) { session.close() }
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
        flow = SignFlow.Idle
    }

    /** Called on window close. A running signing step finishes first; the session closes and zeroes the PIN. */
    fun dispose() {
        runBlocking(cardThread) { }
        cardThread.close()
    }
}
