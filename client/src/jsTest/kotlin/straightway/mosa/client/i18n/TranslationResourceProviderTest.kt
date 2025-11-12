package straightway.mosa.client.i18n

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class TranslationResourceProviderTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `load returns translations when bundle exists`() {
        val provider = provider(
            mapOf(
                "./en.json" to bundle(mapOf("button.connect.label" to "Let's go!")),
                "./pt.json" to bundle(mapOf("button.connect.label" to "Partiu!"))
            )
        )

        val resource = provider.load("pt")

        assertEquals("pt", resource?.tag)
        assertEquals(mapOf("button.connect.label" to "Partiu!"), resource?.entries)
    }

    @Test
    fun `load returns null when bundle missing`() {
        val provider = provider(mapOf("./en.json" to bundle(mapOf("key" to "value"))))

        val resource = provider.load("de")

        assertNull(resource)
    }

    @Test
    fun `fallback returns english translations`() {
        val provider = provider(
            mapOf(
                "./en.json" to bundle(mapOf("button.connect.label" to "Let's go!")),
                "./de.json" to bundle(mapOf("button.connect.label" to "Los geht's!"))
            )
        )

        val fallback = provider.fallback()

        assertEquals("en", fallback.tag)
        assertEquals(mapOf("button.connect.label" to "Let's go!"), fallback.entries)
    }

    @Test
    fun `throws when fallback translations missing`() {
        val provider = provider(emptyMap())

        val exception = assertFails { provider.fallback() }

        assertEquals("Missing fallback translations for en", exception.message)
    }

    private fun provider(bundles: Map<String, dynamic>): TranslationResourceProvider {
        return WebpackTranslationResourceProvider(resourceContext(bundles), json)
    }

    private fun resourceContext(bundles: Map<String, dynamic>): dynamic = { path: String ->
        bundles[path] ?: throw IllegalStateException("Module not found: $path")
    }

    private fun bundle(values: Map<String, String>): dynamic {
        val obj = js("{}")
        values.forEach { (key, value) -> obj[key] = value }
        return obj
    }
}
