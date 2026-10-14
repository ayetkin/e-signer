package sh.yetkin.esigner.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import sh.yetkin.esigner.core.CardSession
import sh.yetkin.esigner.core.DocumentSigner
import sh.yetkin.esigner.core.FormatMode
import sh.yetkin.esigner.core.Pkcs11Library
import sh.yetkin.esigner.core.SigningException
import sh.yetkin.esigner.core.formatFor
import java.io.File
import java.util.concurrent.Executors

sealed interface CardState {
    data object Locked : CardState
    data object Opening : CardState
    data class Open(val session: CardSession, val selectedKey: Int) : CardState
}

sealed interface Phase {
    data object Editing : Phase
    data class Signing(val done: Int, val total: Int, val current: String) : Phase
    data class Finished(val results: List<FileResult>) : Phase
}

data class FileResult(val input: File, val output: File?, val error: String?)

data class UiError(val message: String, val detail: String?)

/**
 * UI state and actions. All card access goes through a single thread, since a PKCS#11 session must
 * not be used concurrently.
 */
class AppState(private val scope: CoroutineScope) {
    private val cardThread = Executors.newSingleThreadExecutor { Thread(it, "card").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    private val signer = DocumentSigner()

    val files = mutableStateListOf<File>()
    var mode by mutableStateOf(FormatMode.AUTO)
    var libraryPath by mutableStateOf(Pkcs11Library.defaultPath())
    var slotIndex by mutableStateOf(0)
    var card by mutableStateOf<CardState>(CardState.Locked)
        private set
    var error by mutableStateOf<UiError?>(null)
        private set
    /** The user confirmed they reviewed exactly the files in the list. Reset whenever the list changes. */
    var confirmed by mutableStateOf(false)
    var phase by mutableStateOf<Phase>(Phase.Editing)
        private set

    val canSign: Boolean
        get() = files.isNotEmpty() && confirmed && card is CardState.Open && phase == Phase.Editing

    fun addFiles(newFiles: List<File>) {
        if (phase != Phase.Editing) return
        val added = newFiles.map { it.absoluteFile }.filter { it.isFile && it !in files }
        if (added.isNotEmpty()) {
            files.addAll(added)
            confirmed = false
        }
    }

    fun removeFile(file: File) {
        if (files.remove(file)) confirmed = false
    }

    fun selectKey(index: Int) {
        (card as? CardState.Open)?.let { card = it.copy(selectedKey = index) }
    }

    /** Single login attempt. Takes ownership of [pin]; on failure it is zeroed and nothing is retried. */
    fun unlock(pin: CharArray) {
        if (card != CardState.Locked || pin.isEmpty()) {
            pin.fill('\u0000')
            return
        }
        card = CardState.Opening
        error = null
        val library = libraryPath.trim()
        val slot = slotIndex
        scope.launch {
            card = try {
                val session = withContext(cardThread) { CardSession.open(library, slot, pin) }
                CardState.Open(session, session.preferredKeyIndex)
            } catch (e: SigningException) {
                error = UiError(e.userMessage, e.technicalDetail)
                CardState.Locked
            }
        }
    }

    fun lock() {
        val open = card as? CardState.Open ?: return
        card = CardState.Locked
        scope.launch { withContext(cardThread) { open.session.close() } }
    }

    fun sign() {
        if (!canSign) return
        val open = card as CardState.Open
        val key = open.session.keys[open.selectedKey]
        val jobs = files.map { it to formatFor(it, mode) }
        error = null
        phase = Phase.Signing(0, jobs.size, jobs.first().first.name)

        scope.launch {
            val results = mutableListOf<FileResult>()
            var cardFailure: String? = null
            for ((index, job) in jobs.withIndex()) {
                val (input, format) = job
                if (cardFailure != null) {
                    results += FileResult(input, null, "Kart hatası nedeniyle imzalanmadı.")
                    continue
                }
                phase = Phase.Signing(index, jobs.size, input.name)
                results += try {
                    val output = withContext(cardThread) { signer.sign(input, format, open.session.connection, key.entry) }
                    FileResult(input, output, null)
                } catch (e: SigningException) {
                    if (e.isCardError) cardFailure = e.userMessage
                    FileResult(input, null, e.userMessage)
                } catch (e: Exception) {
                    FileResult(input, null, "Beklenmeyen hata: ${e.javaClass.simpleName}")
                }
            }
            // Every signing run needs a fresh PIN entry.
            card = CardState.Locked
            withContext(cardThread) { open.session.close() }
            phase = Phase.Finished(results)
        }
    }

    fun startOver() {
        files.clear()
        confirmed = false
        error = null
        phase = Phase.Editing
    }

    fun dismissError() {
        error = null
    }

    /** Called on window close: logs out and zeroes the PIN before the JVM exits. */
    fun dispose() {
        (card as? CardState.Open)?.let { open ->
            card = CardState.Locked
            runBlocking(cardThread) { open.session.close() }
        }
        cardThread.close()
    }
}
