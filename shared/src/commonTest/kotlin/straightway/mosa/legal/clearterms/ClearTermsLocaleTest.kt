package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsLocaleTest {
    private val locale =
        ClearTermsLocale(
            language = "en",
            months = listOf("January", "February", "March"),
            weekdays = mapOf("mon" to "Monday"),
            decimalSeparator = ".",
            groupingSeparator = ",",
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

    @Test
    fun `renders locale date patterns through public methods`() {
        assertEquals("January", locale.monthName(1))
        assertEquals("on January 2, 2026", locale.onFullDate("January", 2, 2026))
        assertEquals("in February 2026", locale.inMonthYear("February", 2026))
        assertEquals("on day 3 of every month in 2026", locale.onDayOfEveryMonthInYear(3, 2026))
        assertEquals("in 2026", locale.inYear(2026))
        assertEquals("on March 4 every year", locale.onMonthDayEveryYear("March", 4))
        assertEquals("every day in January", locale.everyDayInMonth("January"))
        assertEquals("on day 5 of every month", locale.onDayOfEveryMonth(5))
        assertEquals("every day", locale.everyDay())
        assertEquals("on the last day of February 2026", locale.onLastDayOfMonth("February", 2026))
        assertEquals("on the last day of March every year", locale.onLastDayOfMonthEveryYear("March"))
        assertEquals("on the last day of every month in 2026", locale.onLastDayOfEveryMonthInYear(2026))
        assertEquals("on the last day of every month", locale.onLastDayOfEveryMonth())
    }

    @Test
    fun `returns singular and plural labels for supported duration units`() {
        val durationTexts = ClearTermsLocale.DurationTexts()

        assertEquals("minute", durationTexts.labelFor('m', singular = true))
        assertEquals("minutes", durationTexts.labelFor('m', singular = false))
    }

    @Test
    fun `fails for unsupported duration unit`() {
        val durationTexts = ClearTermsLocale.DurationTexts()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                durationTexts.labelFor('q', singular = true)
            }

        assertEquals("Unsupported duration unit 'q'", exception.message)
    }
}
