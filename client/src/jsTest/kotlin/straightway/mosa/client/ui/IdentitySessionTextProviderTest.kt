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
        assertEquals("No pod is configured for this client.", provider.missingPod)
    }

    @Test
    fun `includes failure message in preparation text`() {
        val message = provider.failedToPrepare("offline")

        assertEquals("Failed to prepare identity: offline", message)
    }

    @Test
    fun `formats preview texts for topic`() {
        val header = provider.previewResultsHeader("Topic")
        val empty = provider.previewResultsEmpty("Topic")

        assertEquals("Results for Topic:", header)
        assertEquals("Results for Topic: none", empty)
    }
}
