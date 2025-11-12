package straightway.mosa.client.browser

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserPrefsTest {
    @Test
    fun `returns locales from navigator languages`() {
        withNavigator(js("({ languages: ['de-DE', 'en-US'], language: 'en-US' })")) {
            val prefs = BrowserPrefs()

            assertEquals(listOf("de-DE", "en-US"), prefs.languages.map { it.toString() })
        }
    }

    @Test
    fun `falls back to navigator language when languages missing`() {
        withNavigator(js("({ language: 'fr-FR' })")) {
            val prefs = BrowserPrefs()

            assertEquals(listOf("fr-FR"), prefs.languages.map { it.toString() })
        }
    }

    @Test
    fun `returns empty list if nothing is specified`() {
        withNavigator(js("({ language: null })")) {
            val prefs = BrowserPrefs()

            assertEquals(emptyList(), prefs.languages.map { it.toString() })
        }
    }

    @Test
    fun `returns empty list when navigator is unavailable`() {
        val prefs = BrowserPrefs { null }

        assertEquals(emptyList(), prefs.languages)
    }

    private fun withNavigator(@Suppress("unused") navigator: dynamic, block: () -> Unit) {
        val descriptor = js("Object.getOwnPropertyDescriptor(globalThis, 'navigator')")
        js("Object.defineProperty(globalThis, 'navigator', { value: navigator, configurable: true, writable: true })")
        try {
            block()
        } finally {
            if (descriptor != null) {
                js("Object.defineProperty(globalThis, 'navigator', descriptor)")
            } else {
                js("delete globalThis.navigator")
            }
        }
    }
}
