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

        val translated = translator.translate("button.connect.busy")

        assertEquals("Connecting...", translated)
    }

    @Test
    fun `returns german text for german language`() {
        val translator = Translator(listOf(Locale("de")))

        val translated = translator.translate("button.connect.busy")

        assertEquals("Verbindung wird hergestellt...", translated)
    }

    @Test
    fun `falls back to english when language is unknown`() {
        val translator = Translator(listOf(Locale("fr")))

        val translated = translator.translate("button.connect.busy")

        assertEquals("Connecting...", translated)
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

        val translated = translator.translate("identity.failedToPrepare", mapOf("message" to "Boom"))

        assertEquals("Failed to prepare identity: Boom", translated)
    }
}
