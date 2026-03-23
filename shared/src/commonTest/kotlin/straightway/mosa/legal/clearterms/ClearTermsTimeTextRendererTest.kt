package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.render.ClearTermsTimeTextRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearTermsTimeTextRendererTest {
    @Test
    fun `renders converted local time without timezone suffix`() {
        val renderer =
            ClearTermsTimeTextRenderer(
                timeZoneDisplayConverter =
                    object : ClearTermsTimeZoneDisplayConverter {
                        override fun convert(
                            value: String,
                            parsed: ClearTermsTimeValue,
                        ): ClearTermsTimeDisplayValue? =
                            ClearTermsTimeDisplayValue(
                                year = 2025,
                                month = 1,
                                day = 3,
                                time = "01:15:00",
                            )
                    },
            )

        val rendered = renderer.renderTimeValue("2025-01-02T23:15:00Z", englishLocale())

        assertEquals("on January 3, 2025 at 01:15:00", rendered)
    }

    private fun englishLocale(): ClearTermsLocale =
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
            weekdays = emptyMap(),
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
            duration = ClearTermsLocale.DurationTexts(infinite = "indefinitely"),
        )
}
