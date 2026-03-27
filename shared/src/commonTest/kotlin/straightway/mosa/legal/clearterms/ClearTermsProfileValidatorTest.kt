@file:Suppress("ktlint:standard:no-wildcard-imports")

package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.document.*
import straightway.mosa.legal.clearterms.locale.*
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.render.*
import straightway.mosa.legal.clearterms.spi.*
import straightway.mosa.legal.clearterms.validation.*
import straightway.mosa.legal.clearterms.validation.ProfileValidator
import straightway.mosa.legal.clearterms.value.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClearTermsProfileValidatorTest {
    private val validator = ProfileValidator(PlaceholderParser())

    @Test
    fun `reduces supported languages to subset across profile chain`() {
        val baselineTexts =
            mapOf(
                "de" to listOf(TextBlock(id = "main", text = "DE")),
                "en" to listOf(TextBlock(id = "main", text = "EN")),
                "fr" to listOf(TextBlock(id = "main", text = "FR")),
                "zh" to listOf(TextBlock(id = "main", text = "ZH")),
            )

        val firstProfileTexts =
            mapOf(
                "de" to Profile.TextOverrides(replace = mapOf("main" to "DE 1"), append = null),
                "en" to Profile.TextOverrides(replace = mapOf("main" to "EN 1"), append = null),
                "zh" to Profile.TextOverrides(replace = mapOf("main" to "ZH 1"), append = null),
            )
        val secondProfileTexts =
            mapOf(
                "en" to Profile.TextOverrides(replace = mapOf("main" to "EN 2"), append = null),
                "zh" to Profile.TextOverrides(replace = mapOf("main" to "ZH 2"), append = null),
            )

        val afterFirst = validator.applyTextOverrides(baselineTexts, firstProfileTexts)
        val afterSecond = validator.applyTextOverrides(afterFirst, secondProfileTexts)

        assertEquals(setOf("de", "en", "zh"), afterFirst.keys)
        assertEquals(setOf("en", "zh"), afterSecond.keys)
        assertEquals("EN 2", afterSecond.getValue("en").single().text)
        assertEquals("ZH 2", afterSecond.getValue("zh").single().text)
    }

    @Test
    fun `rejects overrides for language not supported by previous profile state`() {
        val texts =
            mapOf(
                "de" to listOf(TextBlock(id = "main", text = "DE")),
                "en" to listOf(TextBlock(id = "main", text = "EN")),
            )
        val overrides =
            mapOf(
                "de" to Profile.TextOverrides(replace = mapOf("main" to "DE 1"), append = null),
                "fr" to Profile.TextOverrides(replace = mapOf("main" to "FR 1"), append = null),
            )

        val exception =
            assertFailsWith<ValidationException> {
                validator.applyTextOverrides(texts, overrides)
            }

        assertEquals("Override language 'fr' is not supported by parent profile", exception.message)
    }

    @Test
    fun `allows empty override language set and returns empty effective texts`() {
        val texts =
            mapOf(
                "de" to listOf(TextBlock(id = "main", text = "DE")),
                "en" to listOf(TextBlock(id = "main", text = "EN")),
            )

        val result = validator.applyTextOverrides(texts, emptyMap())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `fails final validation when no languages remain`() {
        val exception =
            assertFailsWith<ValidationException> {
                validator.validateFinalTextsResolved(emptyMap())
            }

        assertEquals("No languages available after applying profile chain", exception.message)
    }
}
