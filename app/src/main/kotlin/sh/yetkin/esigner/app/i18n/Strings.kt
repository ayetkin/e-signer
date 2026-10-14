package sh.yetkin.esigner.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale
import java.util.ResourceBundle
import java.util.prefs.Preferences

enum class Language(val tag: String) {
    TR("tr"),
    EN("en");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    fun save() {
        runCatching { prefs.put(KEY, tag) }
    }

    companion object {
        private const val KEY = "language"
        private val prefs: Preferences get() = Preferences.userRoot().node("sh/yetkin/esigner")

        /** Saved choice, otherwise Turkish on Turkish systems and English everywhere else. */
        fun initial(): Language {
            val saved = runCatching { prefs.get(KEY, null) }.getOrNull()
            return entries.firstOrNull { it.tag == saved }
                ?: if (Locale.getDefault().language == TR.tag) TR else EN
        }
    }
}

/** UI text from `i18n/strings_<tag>.properties` (UTF-8). Placeholders are `{0}`, `{1}`, ... */
class Strings private constructor(val language: Language) {
    private val bundle = ResourceBundle.getBundle(
        BUNDLE,
        language.locale,
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
    )

    val keys: Set<String> get() = bundle.keySet()

    fun has(key: String): Boolean = bundle.containsKey(key)

    operator fun get(key: String, vararg args: Any): String {
        // A missing key is visible in the UI instead of crashing; StringsTest keeps both bundles complete.
        var text = if (bundle.containsKey(key)) bundle.getString(key) else "[$key]"
        args.forEachIndexed { index, arg -> text = text.replace("{$index}", arg.toString()) }
        return text
    }

    companion object {
        const val BUNDLE = "i18n.strings"
        private val cache = Language.entries.associateWith { Strings(it) }

        fun of(language: Language): Strings = cache.getValue(language)
    }
}

val LocalStrings = staticCompositionLocalOf { Strings.of(Language.EN) }

@Composable
fun str(key: String, vararg args: Any): String = LocalStrings.current.get(key, *args)
