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

        val translated = translator.translate("button.connect.label")

        assertEquals("Let's go!", translated)
    }

    @Test
    fun `returns german text for german language`() {
        val translator = Translator(listOf(Locale("de")))

        val translated = translator.translate("button.connect.label")

        assertEquals("Los geht's!", translated)
    }

    @Test
    fun `falls back to english when language is unknown`() {
        val translator = Translator(listOf(Locale("fr")))

        val translated = translator.translate("button.connect.label")

        assertEquals("Let's go!", translated)
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

    @Test
    fun `uses language fallback when region specific translations are missing`() {
        val provider = FakeTranslationResourceProvider(
            mapOf(
                "pt" to mapOf("button.connect.label" to "Partiu!"),
                "en" to mapOf("button.connect.label" to "Let's go!")
            )
        )
        val translator = Translator(listOf(Locale("pt-BR")), provider)

        val translated = translator.translate("button.connect.label")

        assertEquals("Partiu!", translated)
    }
}

private class FakeTranslationResourceProvider(
    private val bundles: Map<String, Map<String, String>>,
    private val fallbackTag: String = "en"
) : TranslationResourceProvider {
    override fun load(tag: String): TranslationResource? = bundles[tag]?.let { TranslationResource(tag, it) }

    override fun fallback(): TranslationResource =
        load(fallbackTag) ?: error("Missing fallback translations for $fallbackTag")
}
