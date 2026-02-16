package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsValueRendererTest {
    @Test
    fun `throws when block text is unresolved`() {
        val renderer =
            ClearTermsValueRenderer(
                placeholderParser = ClearTermsPlaceholderParser(),
                localeProvider =
                    object : ClearTermsLocaleProvider {
                        override fun resolve(languageTag: String): ClearTermsLocale = throw IllegalStateException("must not be called")
                    },
            )

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                renderer.renderBlocks(
                    blocks = listOf(ClearTermsTextBlock(id = "contact", text = null)),
                    values = emptyMap(),
                    languageTag = "en",
                )
            }

        assertEquals("Unresolved text block 'contact'", exception.message)
    }

    @Test
    fun `throws when placeholder value is missing`() {
        val renderer =
            ClearTermsValueRenderer(
                placeholderParser = ClearTermsPlaceholderParser(),
                localeProvider =
                    object : ClearTermsLocaleProvider {
                        override fun resolve(languageTag: String): ClearTermsLocale = throw IllegalStateException("must not be called")
                    },
            )

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                renderer.renderBlocks(
                    blocks = listOf(ClearTermsTextBlock(id = "contact", text = "{{string:contact}}")),
                    values = emptyMap(),
                    languageTag = "en",
                )
            }

        assertEquals("Missing value for 'contact'", exception.message)
    }
}
