package straightway.mosa.client.i18n

import js.intl.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

class TranslatorTest {
    @Test
    fun `returns english text for english language`() {
        val translator = Translator(listOf(Locale("en")))

        val translated = translator.translate("landing.title")

        assertEquals("Connect to your MOSA pod", translated)
    }

    @Test
    fun `returns german text for german language`() {
        val translator = Translator(listOf(Locale("de")))

        val translated = translator.translate("landing.title")

        assertEquals("Verbinde dich mit deinem MOSA-Pod", translated)
    }

    @Test
    fun `falls back to english when language is unknown`() {
        val translator = Translator(listOf(Locale("fr")))

        val translated = translator.translate("landing.title")

        assertEquals("Connect to your MOSA pod", translated)
    }

    @Test
    fun `throws when translation missing`() {
        val translator = Translator(listOf(Locale("en")))

        val exception = assertFails { translator.translate("missing.key") }

        assertIs<RuntimeException>(exception)
        assertEquals("No translation for key 'missing.key' in locale en", exception.message)
    }

    @Test
    fun `applies template replacements`() {
        val translator = Translator(listOf(Locale("en")))

        val translated = translator.translate("identity.eyebrow", mapOf("identityName" to "Alice"))

        assertEquals("Identity: Alice", translated)
    }
}
