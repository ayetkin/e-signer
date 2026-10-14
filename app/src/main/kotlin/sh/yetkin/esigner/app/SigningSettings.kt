package sh.yetkin.esigner.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sh.yetkin.esigner.core.HttpTimestampSource
import sh.yetkin.esigner.core.SigningLevel
import sh.yetkin.esigner.core.TimestampAuth
import java.util.prefs.Preferences

enum class TsaAuthType { NONE, BASIC, KAMU_SM }

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
    var authType by mutableStateOf(TsaAuthType.NONE)
        private set
    var user by mutableStateOf("")
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
        }
    }

    /** A timestamp level with no usable TSA configuration; signing is blocked until it is fixed. */
    val missingTsa: Boolean
        get() = level.needsTimestamp && (
            tsaUrl.isBlank() ||
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
        if (authType == TsaAuthType.NONE) clearPassword()
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
    fun timestampFor(): Pair<HttpTimestampSource, TimestampAuth>? {
        if (!level.needsTimestamp || missingTsa) return null
        // missingTsa guarantees a password for the authenticated types.
        val auth = when (authType) {
            TsaAuthType.NONE -> TimestampAuth.None
            TsaAuthType.BASIC -> TimestampAuth.Basic(user, password!!.copyOf())
            TsaAuthType.KAMU_SM -> TimestampAuth.KamuSm(user.toLong(), password!!.copyOf())
        }
        return HttpTimestampSource(tsaUrl, auth) to auth
    }

    internal fun setForTest(level: SigningLevel, url: String = "", authType: TsaAuthType = TsaAuthType.NONE, user: String = "", password: String? = null) {
        update(level, url, authType, user, password?.toCharArray())
    }

    companion object {
        const val KAMU_SM_URL = "http://zd.kamusm.gov.tr"

        private const val LEVEL = "signingLevel"
        private const val URL = "tsaUrl"
        private const val AUTH = "tsaAuth"
        private const val USER = "tsaUser"
        private val prefs: Preferences get() = Preferences.userRoot().node("sh/yetkin/esigner")
    }
}
