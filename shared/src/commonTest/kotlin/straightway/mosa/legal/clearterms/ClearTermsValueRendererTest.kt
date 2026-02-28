package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsValueRendererTest {
    private val renderer = ClearTermsValueRenderer(ClearTermsPlaceholderParser(), TestLocaleProvider())

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

    @Test
    fun `renders all placeholder types in block rendering`() {
        val rendered =
            renderer.renderBlocks(
                blocks =
                    listOf(
                        ClearTermsTextBlock(
                            id = "main",
                            text =
                                "User {{string:name}}, limit {{number:limit}}, duration {{duration:dur}}, time {{time:start}}, weekday {{weekday:day}}.",
                        ),
                    ),
                values =
                    mapOf(
                        "name" to ClearTermsValue(ClearTermsPlaceholderType.STRING, "Alice"),
                        "limit" to ClearTermsValue(ClearTermsPlaceholderType.NUMBER, 12.5),
                        "dur" to ClearTermsValue(ClearTermsPlaceholderType.DURATION, "P1D"),
                        "start" to ClearTermsValue(ClearTermsPlaceholderType.TIME, "2025-01-15"),
                        "day" to ClearTermsValue(ClearTermsPlaceholderType.WEEKDAY, "mon"),
                    ),
                languageTag = "en",
            )

        assertEquals("User Alice, limit 12.5, duration P1D, time on January 15, 2025, weekday Monday.", rendered)
    }

    @Test
    fun `renders fallback when value is null`() {
        val rendered =
            renderer.renderBlocks(
                blocks = listOf(ClearTermsTextBlock(id = "main", text = "Limit {{number:limit??unknown}}.")),
                values = mapOf("limit" to ClearTermsValue(ClearTermsPlaceholderType.NUMBER, null)),
                languageTag = "en",
            )

        assertEquals("Limit unknown.", rendered)
    }

    @Test
    fun `throws when null value has no fallback`() {
        val exception =
            assertFailsWith<ClearTermsValidationException> {
                renderer.renderBlocks(
                    blocks = listOf(ClearTermsTextBlock(id = "main", text = "Limit {{number:limit}}.")),
                    values = mapOf("limit" to ClearTermsValue(ClearTermsPlaceholderType.NUMBER, null)),
                    languageTag = "en",
                )
            }

        assertEquals("Missing fallback for 'limit'", exception.message)
    }

    @Test
    fun `renders block segments with configured and default backgrounds and merges adjacent`() {
        val segments =
            renderer.renderBlockSegments(
                block = ClearTermsTextBlock(id = "main", text = "{{string:a}}{{string:b}} {{string:c}}"),
                values =
                    mapOf(
                        "a" to ClearTermsValue(ClearTermsPlaceholderType.STRING, "A"),
                        "b" to ClearTermsValue(ClearTermsPlaceholderType.STRING, "B"),
                        "c" to ClearTermsValue(ClearTermsPlaceholderType.STRING, "C"),
                    ),
                languageTag = "en",
                placeholderBackgroundByKey =
                    mapOf(
                        "a" to ClearTermsRenderBackground.YELLOW,
                        "b" to ClearTermsRenderBackground.YELLOW,
                    ),
            )

        assertEquals(
            listOf(
                ClearTermsRenderedBlock.Segment("AB", ClearTermsRenderBackground.YELLOW),
                ClearTermsRenderedBlock.Segment(" C", ClearTermsRenderBackground.NONE),
            ),
            segments,
        )
    }

    @Test
    fun `returns empty segment list when only empty placeholder values are rendered`() {
        val segments =
            renderer.renderBlockSegments(
                block = ClearTermsTextBlock(id = "main", text = "{{string:a}}"),
                values = mapOf("a" to ClearTermsValue(ClearTermsPlaceholderType.STRING, "")),
                languageTag = "en",
                placeholderBackgroundByKey = emptyMap(),
            )

        assertEquals(emptyList(), segments)
    }

    @Test
    fun `throws when unresolved placeholder syntax remains in rendered text`() {
        val exception =
            assertFailsWith<ClearTermsValidationException> {
                renderer.ensureNoUnresolvedPlaceholders("Hello {{string:name}}")
            }

        assertEquals("Rendered text contains unresolved placeholders", exception.message)
    }

    @Test
    fun `throws when rendered text contains unterminated placeholder`() {
        val exception =
            assertFailsWith<ClearTermsValidationException> {
                renderer.ensureNoUnresolvedPlaceholders("Hello {{string:name")
            }

        assertEquals("Rendered text contains unterminated placeholder", exception.message)
    }

    @Test
    fun `accepts non placeholder brace sequences`() {
        renderer.ensureNoUnresolvedPlaceholders("Value {{not a placeholder}} and {{string:name?}}")
    }

    @Test
    fun `accepts escaped looking content and placeholder with whitespace key`() {
        renderer.ensureNoUnresolvedPlaceholders("{{string:with space}}")
    }

    @Test
    fun `accepts regular text without placeholder patterns`() {
        renderer.ensureNoUnresolvedPlaceholders("No braces here")
    }

    private class TestLocaleProvider : ClearTermsLocaleProvider {
        override fun resolve(languageTag: String): ClearTermsLocale =
            ClearTermsLocale(
                language = "en",
                months =
                    listOf(
                        "January",
                        "February",
                        "March",
                        "April",
                        "May",
                        "June",
                        "July",
                        "August",
                        "September",
                        "October",
                        "November",
                        "December",
                    ),
                weekdays =
                    mapOf(
                        "mon" to "Monday",
                        "tue" to "Tuesday",
                        "wed" to "Wednesday",
                        "thu" to "Thursday",
                        "fri" to "Friday",
                        "sat" to "Saturday",
                        "sun" to "Sunday",
                    ),
                decimalSeparator = '.',
                groupingSeparator = ',',
                atWord = "at",
                patterns =
                    ClearTermsLocale.Patterns(
                        onFullDate = "on {month} {day}, {year}",
                        inMonthYear = "in {month} {year}",
                        onDayOfEveryMonthInYear = "on day {day} of every month in {year}",
                        inYear = "in {year}",
                        onMonthDayEveryYear = "on {month} {day} every year",
                        everyDayInMonth = "every day in {month}",
                        onDayOfEveryMonth = "on day {day} of every month",
                        everyDay = "every day",
                        onLastDayOfMonth = "on the last day of {month} {year}",
                        onLastDayOfMonthEveryYear = "on the last day of {month} every year",
                        onLastDayOfEveryMonthInYear = "on the last day of every month in {year}",
                        onLastDayOfEveryMonth = "on the last day of every month",
                    ),
            )
    }
}
