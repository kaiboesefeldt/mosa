package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.render.ClearTermsDurationTextRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsDurationTextRendererTest {
    private val renderer = ClearTermsDurationTextRenderer()
    private val englishLocale = locale(language = "en", decimalSeparator = ".", groupingSeparator = ",")
    private val germanLocale =
        locale(
            language = "de",
            decimalSeparator = ",",
            groupingSeparator = ".",
            duration =
                ClearTermsLocale.DurationTexts(
                    infinite = "unbegrenzt",
                    units =
                        mapOf(
                            "s" to ClearTermsLocale.DurationTexts.UnitTexts("Sekunde", "Sekunden"),
                            "m" to ClearTermsLocale.DurationTexts.UnitTexts("Minute", "Minuten"),
                            "h" to ClearTermsLocale.DurationTexts.UnitTexts("Stunde", "Stunden"),
                            "d" to ClearTermsLocale.DurationTexts.UnitTexts("Tag", "Tage"),
                            "w" to ClearTermsLocale.DurationTexts.UnitTexts("Woche", "Wochen"),
                            "y" to ClearTermsLocale.DurationTexts.UnitTexts("Jahr", "Jahre"),
                        ),
                ),
        )

    @Test
    fun `renders infinite marker from locale`() {
        assertEquals("unbegrenzt", renderer.renderDurationValue(rawValue = "infinite", locale = germanLocale))
    }

    @Test
    fun `normalizes quoted and padded scalar values`() {
        assertEquals("14 days", renderer.renderDurationValue(rawValue = " \"14d\" ", locale = englishLocale))
    }

    @Test
    fun `renders singular and plural units`() {
        assertEquals("1 day", renderer.renderDurationValue(rawValue = "1d", locale = englishLocale))
        assertEquals("2 days", renderer.renderDurationValue(rawValue = "2d", locale = englishLocale))
    }

    @Test
    fun `uses singular label for negative one by absolute value`() {
        assertEquals("-1 day", renderer.renderDurationValue(rawValue = "-1d", locale = englishLocale))
    }

    @Test
    fun `renders decimal quantities with locale decimal separator and no grouping`() {
        assertEquals("1,5 Tage", renderer.renderDurationValue(rawValue = "1.5d", locale = germanLocale))
        assertEquals("1000 days", renderer.renderDurationValue(rawValue = "1000d", locale = englishLocale))
    }

    @Test
    fun `throws for too short normalized value`() {
        val exception =
            assertFailsWith<ClearTermsValidationException> {
                renderer.renderDurationValue(rawValue = "d", locale = englishLocale)
            }

        assertEquals("Invalid duration value 'd'", exception.message)
    }

    @Test
    fun `throws for non numeric quantity`() {
        val exception =
            assertFailsWith<ClearTermsValidationException> {
                renderer.renderDurationValue(rawValue = "xd", locale = englishLocale)
            }

        assertEquals("Invalid duration value 'xd'", exception.message)
    }

    @Test
    fun `throws for unsupported duration unit`() {
        val exception =
            assertFailsWith<ClearTermsValidationException> {
                renderer.renderDurationValue(rawValue = "5q", locale = englishLocale)
            }

        assertEquals("Unsupported duration unit 'q'", exception.message)
    }

    private fun locale(
        language: String,
        decimalSeparator: String,
        groupingSeparator: String,
        duration: ClearTermsLocale.DurationTexts = ClearTermsLocale.DurationTexts(infinite = "indefinitely"),
    ): ClearTermsLocale =
        ClearTermsLocale(
            language = language,
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
            decimalSeparator = decimalSeparator,
            groupingSeparator = groupingSeparator,
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
            duration = duration,
        )
}
