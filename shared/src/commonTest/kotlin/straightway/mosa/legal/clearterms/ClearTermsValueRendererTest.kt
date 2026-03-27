@file:Suppress("ktlint:standard:no-wildcard-imports")

package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.document.*
import straightway.mosa.legal.clearterms.locale.*
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.placeholder.PlaceholderType
import straightway.mosa.legal.clearterms.render.*
import straightway.mosa.legal.clearterms.render.ValueRenderer
import straightway.mosa.legal.clearterms.spi.*
import straightway.mosa.legal.clearterms.validation.*
import straightway.mosa.legal.clearterms.value.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsValueRendererTest {
    private val renderer = ValueRenderer(PlaceholderParser(), TestLocaleProvider())

    @Test
    fun `throws when block text is unresolved`() {
        val renderer =
            ValueRenderer(
                placeholderParser = PlaceholderParser(),
                localeProvider =
                    object : LocaleProvider {
                        override fun resolve(languageTag: String): Locale = throw IllegalStateException("must not be called")
                    },
            )

        val exception =
            assertFailsWith<ValidationException> {
                renderer.renderBlocks(
                    blocks = listOf(TextBlock(id = "contact", text = null)),
                    values = emptyMap(),
                    languageTag = "en",
                )
            }

        assertEquals("Unresolved text block 'contact'", exception.message)
    }

    @Test
    fun `throws when placeholder value is missing`() {
        val exception =
            assertFailsWith<ValidationException> {
                renderer.renderBlocks(
                    blocks = listOf(TextBlock(id = "contact", text = "{{string:contact}}")),
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
                        TextBlock(
                            id = "main",
                            text =
                                "User {{string:name}}, limit {{number:limit}}, duration {{duration:dur}}, time {{time:start}}, weekday {{weekday:day}}.",
                        ),
                    ),
                values =
                    mapOf(
                        "name" to Value(PlaceholderType.STRING, "Alice"),
                        "limit" to Value(PlaceholderType.NUMBER, 12.5),
                        "dur" to Value(PlaceholderType.DURATION, "1d"),
                        "start" to Value(PlaceholderType.TIME, "2025-01-15"),
                        "day" to Value(PlaceholderType.WEEKDAY, "mon"),
                    ),
                languageTag = "en",
            )

        assertEquals("User Alice, limit 12.5, duration 1 day, time on January 15, 2025, weekday Monday.", rendered)
    }

    @Test
    fun `renders duration values localized in german`() {
        val rendered =
            renderer.renderBlocks(
                blocks =
                    listOf(
                        TextBlock(
                            id = "main",
                            text = "Aufbewahrung: {{duration:dur}}.",
                        ),
                    ),
                values = mapOf("dur" to Value(PlaceholderType.DURATION, "14d")),
                languageTag = "de",
            )

        assertEquals("Aufbewahrung: 14 Tage.", rendered)
    }

    @Test
    fun `renders infinite duration localized`() {
        val rendered =
            renderer.renderBlocks(
                blocks =
                    listOf(
                        TextBlock(
                            id = "main",
                            text = "Retention: {{duration:dur}}.",
                        ),
                    ),
                values = mapOf("dur" to Value(PlaceholderType.DURATION, "infinite")),
                languageTag = "en",
            )

        assertEquals("Retention: indefinitely.", rendered)
    }

    @Test
    fun `renders fallback when value is null`() {
        val rendered =
            renderer.renderBlocks(
                blocks = listOf(TextBlock(id = "main", text = "Limit {{number:limit??unknown}}.")),
                values = mapOf("limit" to Value(PlaceholderType.NUMBER, null)),
                languageTag = "en",
            )

        assertEquals("Limit unknown.", rendered)
    }

    @Test
    fun `throws when null value has no fallback`() {
        val exception =
            assertFailsWith<ValidationException> {
                renderer.renderBlocks(
                    blocks = listOf(TextBlock(id = "main", text = "Limit {{number:limit}}.")),
                    values = mapOf("limit" to Value(PlaceholderType.NUMBER, null)),
                    languageTag = "en",
                )
            }

        assertEquals("Missing fallback for 'limit'", exception.message)
    }

    @Test
    fun `renders block segments with configured and default backgrounds and merges adjacent`() {
        val segments =
            renderer.renderBlockSegments(
                block = TextBlock(id = "main", text = "{{string:a}}{{string:b}} {{string:c}}"),
                values =
                    mapOf(
                        "a" to Value(PlaceholderType.STRING, "A"),
                        "b" to Value(PlaceholderType.STRING, "B"),
                        "c" to Value(PlaceholderType.STRING, "C"),
                    ),
                languageTag = "en",
                placeholderBackgroundByKey =
                    mapOf(
                        "a" to RenderBackground.YELLOW,
                        "b" to RenderBackground.YELLOW,
                    ),
                placeholderIsBaselineOverrideByKey = emptyMap(),
            )

        assertEquals(
            listOf(
                RenderedBlock.Segment("AB", RenderBackground.YELLOW),
                RenderedBlock.Segment(" C", RenderBackground.NONE),
            ),
            segments,
        )
    }

    @Test
    fun `returns empty segment list when only empty placeholder values are rendered`() {
        val segments =
            renderer.renderBlockSegments(
                block = TextBlock(id = "main", text = "{{string:a}}"),
                values = mapOf("a" to Value(PlaceholderType.STRING, "")),
                languageTag = "en",
                placeholderBackgroundByKey = emptyMap(),
                placeholderIsBaselineOverrideByKey = emptyMap(),
            )

        assertEquals(emptyList(), segments)
    }

    @Test
    fun `throws when unresolved placeholder syntax remains in rendered text`() {
        val exception =
            assertFailsWith<ValidationException> {
                renderer.ensureNoUnresolvedPlaceholders("Hello {{string:name}}")
            }

        assertEquals("Rendered text contains unresolved placeholders", exception.message)
    }

    @Test
    fun `throws when rendered text contains unterminated placeholder`() {
        val exception =
            assertFailsWith<ValidationException> {
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

    private class TestLocaleProvider : LocaleProvider {
        override fun resolve(languageTag: String): Locale =
            if (languageTag.lowercase().startsWith("de")) {
                germanLocale()
            } else {
                englishLocale()
            }

        private fun englishLocale(): Locale =
            Locale(
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
                decimalSeparator = ".",
                groupingSeparator = ",",
                atWord = "at",
                patterns =
                    Locale.Patterns(
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
                duration =
                    Locale.DurationTexts(
                        infinite = "indefinitely",
                    ),
            )

        private fun germanLocale(): Locale =
            Locale(
                language = "de",
                months =
                    listOf(
                        "Januar",
                        "Februar",
                        "März",
                        "April",
                        "Mai",
                        "Juni",
                        "Juli",
                        "August",
                        "September",
                        "Oktober",
                        "November",
                        "Dezember",
                    ),
                weekdays =
                    mapOf(
                        "mon" to "Montag",
                        "tue" to "Dienstag",
                        "wed" to "Mittwoch",
                        "thu" to "Donnerstag",
                        "fri" to "Freitag",
                        "sat" to "Samstag",
                        "sun" to "Sonntag",
                    ),
                decimalSeparator = ",",
                groupingSeparator = ".",
                atWord = "um",
                patterns =
                    Locale.Patterns(
                        onFullDate = "am {day}. {month} {year}",
                        inMonthYear = "im {month} {year}",
                        onDayOfEveryMonthInYear = "am {day}. jedes Monats im Jahr {year}",
                        inYear = "im Jahr {year}",
                        onMonthDayEveryYear = "am {day}. {month} jedes Jahr",
                        everyDayInMonth = "jeden Tag im {month}",
                        onDayOfEveryMonth = "am {day}. jedes Monats",
                        everyDay = "jeden Tag",
                        onLastDayOfMonth = "am letzten Tag des {month} {year}",
                        onLastDayOfMonthEveryYear = "am letzten Tag des {month} jedes Jahr",
                        onLastDayOfEveryMonthInYear = "am letzten Tag jedes Monats im Jahr {year}",
                        onLastDayOfEveryMonth = "am letzten Tag jedes Monats",
                    ),
                duration =
                    Locale.DurationTexts(
                        infinite = "unbegrenzt",
                        units =
                            mapOf(
                                "s" to Locale.DurationTexts.UnitTexts("Sekunde", "Sekunden"),
                                "m" to Locale.DurationTexts.UnitTexts("Minute", "Minuten"),
                                "h" to Locale.DurationTexts.UnitTexts("Stunde", "Stunden"),
                                "d" to Locale.DurationTexts.UnitTexts("Tag", "Tage"),
                                "w" to Locale.DurationTexts.UnitTexts("Woche", "Wochen"),
                                "y" to Locale.DurationTexts.UnitTexts("Jahr", "Jahre"),
                            ),
                    ),
            )
    }
}
