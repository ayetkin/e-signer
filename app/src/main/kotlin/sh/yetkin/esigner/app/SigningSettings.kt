package sh.yetkin.esigner.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import sh.yetkin.esigner.core.HttpTimestampSource
import sh.yetkin.esigner.core.Pkcs11Library
import sh.yetkin.esigner.core.SigningLevel
import sh.yetkin.esigner.core.TimestampAuth
import sh.yetkin.esigner.core.VisibleStamp
import java.util.prefs.Preferences

/** TSA credentials; every provider asks for them, so there is no type without authentication. */
enum class TsaAuthType { KAMU_SM, BASIC }

/** How the main window opens. */
enum class WindowMode { MAXIMIZED, LAST, DEFAULT }

/** Light or dark colors; [SYSTEM] follows the operating system. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What is drawn on signed PDFs; [NONE] keeps the signature invisible. */
enum class StampStyle(val text: Boolean, val icon: Boolean) {
    NONE(false, false), TEXT(true, false), ICON(false, true), ICON_TEXT(true, true)
}

/**
 * Signature level and time-stamping authority. Level, URL, authentication type and user name (or Kamu SM
 * customer number) are remembered in user preferences. The password is held only in memory for the
 * lifetime of the app and never written anywhere.
 */
class SigningSettings(private val persist: Boolean = true) {
    var level by mutableStateOf(SigningLevel.BASELINE_B)
        private set
    var tsaUrl by mutableStateOf("")
        private set
    var authType by mutableStateOf(TsaAuthType.KAMU_SM)
        private set
    var user by mutableStateOf("")
        private set

    var stamp by mutableStateOf(StampStyle.NONE)
        private set
    /** Height of the visible signature in points. */
    var stampHeight by mutableStateOf(VisibleStamp.DEFAULT_HEIGHT)
        private set

    /**
     * Optional /Reason and /Location written into PDF signatures; empty leaves them out (the signer trims them).
     * [reason] null means the default text in the app's language, so it follows a language change.
     */
    var reason by mutableStateOf<String?>(null)
        private set
    var location by mutableStateOf("")
        private set

    var libraryPath by mutableStateOf(Pkcs11Library.defaultPath())
        private set
    var windowMode by mutableStateOf(WindowMode.MAXIMIZED)
        private set
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    /** Asks GitHub for a new version once at startup; off keeps the app from going online unasked. */
    var checkUpdatesAtStart by mutableStateOf(true)
        private set
    /** Window size when it was last closed un-maximized; used by [WindowMode.LAST]. */
    var lastWindowSize by mutableStateOf(DEFAULT_WINDOW_SIZE)
        private set

    private var password: CharArray? = null
    var hasPassword by mutableStateOf(false)
        private set

    init {
        if (persist) runCatching {
            level = SigningLevel.entries.firstOrNull { it.name == prefs.get(LEVEL, null) } ?: level
            tsaUrl = prefs.get(URL, "")
            authType = TsaAuthType.entries.firstOrNull { it.name == prefs.get(AUTH, null) } ?: authType
            user = prefs.get(USER, "")
            stamp = StampStyle.entries.firstOrNull { it.name == prefs.get(STAMP, null) } ?: stamp
            stampHeight = prefs.getFloat(STAMP_HEIGHT, stampHeight).coerceIn(VisibleStamp.MIN_HEIGHT, VisibleStamp.MAX_HEIGHT)
            libraryPath = prefs.get(LIBRARY, libraryPath)
            reason = prefs.get(REASON, null)
            location = prefs.get(LOCATION, "")
            windowMode = WindowMode.entries.firstOrNull { it.name == prefs.get(WINDOW_MODE, null) } ?: windowMode
            checkUpdatesAtStart = prefs.getBoolean(UPDATES_AT_START, true)
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.get(THEME, null) } ?: themeMode
            lastWindowSize = DpSize(prefs.getInt(WINDOW_WIDTH, 1120).dp, prefs.getInt(WINDOW_HEIGHT, 760).dp)
        }
    }

    fun updateStamp(value: StampStyle) {
        stamp = value
        if (persist) runCatching { prefs.put(STAMP, value.name) }
    }

    /** A [reason] that is the [defaultReason] (spaces aside) is kept as the default, not as fixed text. */
    fun updatePdfDetails(reason: String, location: String, defaultReason: String) {
        this.reason = reason.takeUnless { it.trim() == defaultReason }
        this.location = location
        if (persist) runCatching {
            this.reason?.let { prefs.put(REASON, it) } ?: prefs.remove(REASON)
            prefs.put(LOCATION, this.location)
        }
    }

    fun updateStampHeight(value: Float) {
        stampHeight = value.coerceIn(VisibleStamp.MIN_HEIGHT, VisibleStamp.MAX_HEIGHT)
        if (persist) runCatching { prefs.putFloat(STAMP_HEIGHT, stampHeight) }
    }

    fun updateLibraryPath(value: String) {
        libraryPath = value.trim()
        if (persist) runCatching { prefs.put(LIBRARY, libraryPath) }
    }

    fun updateWindowMode(value: WindowMode) {
        windowMode = value
        if (persist) runCatching { prefs.put(WINDOW_MODE, value.name) }
    }

    fun updateThemeMode(value: ThemeMode) {
        themeMode = value
        if (persist) runCatching { prefs.put(THEME, value.name) }
    }

    fun updateCheckUpdatesAtStart(value: Boolean) {
        checkUpdatesAtStart = value
        if (persist) runCatching { prefs.putBoolean(UPDATES_AT_START, value) }
    }

    fun rememberWindowSize(size: DpSize) {
        lastWindowSize = size
        if (persist) runCatching {
            prefs.putInt(WINDOW_WIDTH, size.width.value.toInt())
            prefs.putInt(WINDOW_HEIGHT, size.height.value.toInt())
        }
    }

    /** A timestamp level with no usable TSA configuration; signing is blocked until it is fixed. */
    val missingTsa: Boolean
        get() = level.needsTimestamp && (
            tsaUrl.isBlank() ||
                needsKamuSmAuth(tsaUrl, authType) ||
                authType == TsaAuthType.BASIC && (user.isBlank() || !hasPassword) ||
                authType == TsaAuthType.KAMU_SM && (user.toLongOrNull() == null || !hasPassword)
            )

    /** Host of the TSA for summaries, e.g. "zd.kamusm.gov.tr". */
    val tsaHost: String get() = runCatching { java.net.URI(tsaUrl.trim()).host }.getOrNull() ?: tsaUrl.trim()

    /**
     * Applies the settings dialog. [newPassword] replaces the kept password when not null and is zeroed
     * here; an empty array clears it.
     */
    fun update(level: SigningLevel, url: String, authType: TsaAuthType, user: String, newPassword: CharArray?) {
        this.level = level
        this.tsaUrl = url.trim()
        this.authType = authType
        this.user = user.trim()
        if (newPassword != null) {
            password?.fill('\u0000')
            password = newPassword.takeIf { it.isNotEmpty() }?.copyOf()
            newPassword.fill('\u0000')
        }
        hasPassword = password != null
        if (persist) runCatching {
            prefs.put(LEVEL, level.name)
            prefs.put(URL, tsaUrl)
            prefs.put(AUTH, authType.name)
            prefs.put(USER, this.user)
        }
    }

    fun clearPassword() {
        password?.fill('\u0000')
        password = null
        hasPassword = false
    }

    /**
     * Credentials for one signing run, with their own copy of the password; the caller clears them
     * when the run ends. Null when no timestamp is needed.
     */
    fun timestampFor(): Pair<HttpTimestampSource, TimestampAuth>? =
        if (!level.needsTimestamp || missingTsa) null else timestampFor(tsaUrl, authType, user, null)

    /**
     * Like [timestampFor], for values not saved yet (the settings dialog checks them before saving). [newPassword]
     * is copied; without it the kept password is used. Null when the values are incomplete.
     */
    fun timestampFor(url: String, authType: TsaAuthType, user: String, newPassword: CharArray?): Pair<HttpTimestampSource, TimestampAuth>? {
        if (url.isBlank() || needsKamuSmAuth(url, authType)) return null
        val secret = (newPassword ?: password)?.takeIf { it.isNotEmpty() } ?: return null
        val auth = when (authType) {
            TsaAuthType.BASIC -> user.trim().takeIf { it.isNotEmpty() }?.let { TimestampAuth.Basic(it, secret.copyOf()) }
            TsaAuthType.KAMU_SM -> user.trim().toLongOrNull()?.let { TimestampAuth.KamuSm(it, secret.copyOf()) }
        } ?: return null
        return HttpTimestampSource(url.trim(), auth) to auth
    }

    internal fun setForTest(level: SigningLevel, url: String = "", authType: TsaAuthType = TsaAuthType.KAMU_SM, user: String = "", password: String? = null) {
        update(level, url, authType, user, password?.toCharArray())
    }

    companion object {
        const val KAMU_SM_URL = "http://zd.kamusm.gov.tr"

        /** Kamu SM's TSA answers only requests with its identity header, so its address needs the Kamu SM type. */
        fun needsKamuSmAuth(url: String, authType: TsaAuthType): Boolean {
            val host = runCatching { java.net.URI(url.trim()).host }.getOrNull()?.lowercase() ?: return false
            return (host == "kamusm.gov.tr" || host.endsWith(".kamusm.gov.tr")) && authType != TsaAuthType.KAMU_SM
        }
        val DEFAULT_WINDOW_SIZE = DpSize(1120.dp, 760.dp)

        /** The card screen is a small centered window; the workspace gets the size chosen above. */
        val CARD_WINDOW_SIZE = DpSize(720.dp, 720.dp)

        private const val LEVEL = "signingLevel"
        private const val URL = "tsaUrl"
        private const val AUTH = "tsaAuth"
        private const val USER = "tsaUser"
        private const val STAMP = "pdfStamp"
        private const val STAMP_HEIGHT = "pdfStampHeight"
        private const val REASON = "pdfReason"
        private const val LOCATION = "pdfLocation"
        private const val LIBRARY = "pkcs11Library"
        private const val WINDOW_MODE = "windowMode"
        private const val THEME = "theme"
        private const val UPDATES_AT_START = "checkUpdatesAtStart"
        private const val WINDOW_WIDTH = "windowWidth"
        private const val WINDOW_HEIGHT = "windowHeight"
        private val prefs: Preferences get() = Preferences.userRoot().node("sh/yetkin/esigner")
    }
}
