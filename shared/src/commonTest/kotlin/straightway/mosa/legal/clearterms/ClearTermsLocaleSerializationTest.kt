@file:Suppress("ktlint:standard:no-wildcard-imports")

package straightway.mosa.legal.clearterms

import kotlinx.serialization.json.Json
import straightway.mosa.legal.clearterms.document.*
import straightway.mosa.legal.clearterms.locale.*
import straightway.mosa.legal.clearterms.render.*
import straightway.mosa.legal.clearterms.spi.*
import straightway.mosa.legal.clearterms.validation.*
import straightway.mosa.legal.clearterms.value.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearTermsLocaleSerializationTest {
    @Test
    fun `serializes and deserializes locale`() {
        val locale =
            Locale(
                language = "en",
                months = listOf("January", "February"),
                weekdays = mapOf("mon" to "Monday"),
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
            )

        val json = Json.encodeToString(locale)

        val decoded = Json.decodeFromString<Locale>(json)

        assertEquals(locale, decoded)
    }

    @Test
    fun `serializes and deserializes locale patterns`() {
        val patterns =
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
            )

        val json = Json.encodeToString(patterns)

        val decoded = Json.decodeFromString<Locale.Patterns>(json)

        assertEquals(patterns, decoded)
    }
}
