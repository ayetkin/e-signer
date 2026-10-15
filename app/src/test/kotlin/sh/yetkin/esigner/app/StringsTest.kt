package sh.yetkin.esigner.app

import sh.yetkin.esigner.app.i18n.Language
import sh.yetkin.esigner.app.i18n.Strings
import sh.yetkin.esigner.core.SigningError
import sh.yetkin.esigner.core.SigningLevel
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {
    private val bundles = Language.entries.associateWith { Strings.of(it) }

    @Test
    fun `all languages define the same keys`() {
        val reference = bundles.getValue(Language.EN).keys
        bundles.forEach { (language, strings) ->
            assertEquals(emptySet(), reference - strings.keys, "missing in $language")
            assertEquals(emptySet(), strings.keys - reference, "only in $language")
        }
    }

    @Test
    fun `every key used in the code exists`() {
        val pattern = Regex("""(?:str\(|strings\[)"([a-zA-Z.]+)"""")
        val used = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { file -> pattern.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        assertTrue(used.isNotEmpty())
        val missing = used - bundles.getValue(Language.EN).keys
        assertEquals(emptySet(), missing)
    }

    @Test
    fun `every error code has a message`() {
        val keys = bundles.getValue(Language.EN).keys
        SigningError.entries.forEach { assertTrue("error.${it.name}" in keys, it.name) }
    }

    @Test
    fun `every level and authentication type has text`() {
        val keys = bundles.getValue(Language.EN).keys
        SigningLevel.entries.forEach {
            assertTrue("level.${it.name}" in keys, it.name)
            assertTrue("level.${it.name}.hint" in keys, it.name)
        }
        TsaAuthType.entries.forEach { assertTrue("settings.tsa.auth.${it.name}" in keys, it.name) }
        SettingsSection.entries.forEach { assertTrue("settings.section.${it.name}" in keys, it.name) }
        WindowMode.entries.forEach {
            assertTrue("settings.window.${it.name}" in keys, it.name)
            assertTrue("settings.window.${it.name}.hint" in keys, it.name)
        }
        ThemeMode.entries.forEach { assertTrue("settings.theme.${it.name}" in keys, it.name) }
        Language.entries.forEach { assertTrue("language.${it.name}" in keys, it.name) }
        StampStyle.entries.forEach {
            assertTrue("stamp.${it.name}" in keys, it.name)
            assertTrue("stamp.${it.name}.hint" in keys, it.name)
        }
    }

    @Test
    fun `placeholders are filled`() {
        assertEquals("Sign (3)", bundles.getValue(Language.EN)["sign.buttonCount", 3])
    }
}
