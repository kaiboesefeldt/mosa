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
        assertEquals("Generating snapshot...", provider.freezeInProgress)
        assertEquals("Snapshot downloaded. It is the only restorable copy.", provider.freezeSuccess)
        assertEquals("Restoring snapshot...", provider.restoreInProgress)
        assertEquals("Could not restore snapshot. Please try again.", provider.restoreError)
    }

    @Test
    fun `includes failure message in preparation text`() {
        val message = provider.failedToPrepare("offline")

        assertEquals("Failed to prepare identity: offline", message)
    }

    @Test
    fun `includes restored count in restore success text`() {
        val message = provider.restoreSuccess(3)

        assertEquals("Snapshot restored: 3 posts and replies.", message)
    }
}
