package straightway.mosa.client.ui

import js.intl.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import straightway.mosa.client.i18n.Translator

class IdentitySessionTextProviderTest {
    private val translator = Translator(listOf(Locale("en")))
    private val provider = TranslatorIdentitySessionTextProvider(translator)

    @Test
    fun `exposes static translations`() {
        assertEquals("...", provider.loading)
        assertEquals("Unknown error", provider.unknownError)
    }

    @Test
    fun `includes failure message in preparation text`() {
        val message = provider.failedToPrepare("offline")

        assertEquals("Failed to prepare identity: offline", message)
    }
}
