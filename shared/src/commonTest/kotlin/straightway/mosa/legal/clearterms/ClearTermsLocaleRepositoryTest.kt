package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals

class ClearTermsLocaleRepositoryTest {
    private val repository = ClearTermsLocaleRepository(FakeClearTermsResourceReader())

    @Test
    fun `resolves german locale from resources`() {
        val locale = repository.resolve("de-DE")

        assertEquals(',', locale.decimalSeparator)
        assertEquals("Februar", locale.monthName(2))
        assertEquals("um", locale.atWord)
    }

    @Test
    fun `falls back to english locale when unknown`() {
        val locale = repository.resolve("fr")

        assertEquals("January", locale.monthName(1))
    }

    private class FakeClearTermsResourceReader : ClearTermsResourceReader {
        private val resources =
            mapOf(
                "clearterms/locales/index.json" to """["en","de"]""",
                "clearterms/locales/en.json" to
                    """
                    {
                      "language": "en",
                      "months": ["January","February","March","April","May","June","July","August","September","October","November","December"],
                      "weekdays": {"mon":"Monday","tue":"Tuesday","wed":"Wednesday","thu":"Thursday","fri":"Friday","sat":"Saturday","sun":"Sunday"},
                      "decimalSeparator": ".",
                      "groupingSeparator": ",",
                      "atWord": "at",
                      "patterns": {
                        "onFullDate": "on {month} {day}, {year}",
                        "inMonthYear": "in {month} {year}",
                        "onDayOfEveryMonthInYear": "on day {day} of every month in {year}",
                        "inYear": "in {year}",
                        "onMonthDayEveryYear": "on {month} {day} every year",
                        "everyDayInMonth": "every day in {month}",
                        "onDayOfEveryMonth": "on day {day} of every month",
                        "everyDay": "every day",
                        "onLastDayOfMonth": "on the last day of {month} {year}",
                        "onLastDayOfMonthEveryYear": "on the last day of {month} every year",
                        "onLastDayOfEveryMonthInYear": "on the last day of every month in {year}",
                        "onLastDayOfEveryMonth": "on the last day of every month"
                      }
                    }
                    """.trimIndent(),
                "clearterms/locales/de.json" to
                    """
                    {
                      "language": "de",
                      "months": ["Januar","Februar","Maerz","April","Mai","Juni","Juli","August","September","Oktober","November","Dezember"],
                      "weekdays": {"mon":"Montag","tue":"Dienstag","wed":"Mittwoch","thu":"Donnerstag","fri":"Freitag","sat":"Samstag","sun":"Sonntag"},
                      "decimalSeparator": ",",
                      "groupingSeparator": ".",
                      "atWord": "um",
                      "patterns": {
                        "onFullDate": "am {day}. {month} {year}",
                        "inMonthYear": "im {month} {year}",
                        "onDayOfEveryMonthInYear": "am {day}. jedes Monats im Jahr {year}",
                        "inYear": "im Jahr {year}",
                        "onMonthDayEveryYear": "am {day}. {month} jedes Jahr",
                        "everyDayInMonth": "jeden Tag im {month}",
                        "onDayOfEveryMonth": "am {day}. jedes Monats",
                        "everyDay": "jeden Tag",
                        "onLastDayOfMonth": "am letzten Tag des {month} {year}",
                        "onLastDayOfMonthEveryYear": "am letzten Tag des {month} jedes Jahr",
                        "onLastDayOfEveryMonthInYear": "am letzten Tag jedes Monats im Jahr {year}",
                        "onLastDayOfEveryMonth": "am letzten Tag jedes Monats"
                      }
                    }
                    """.trimIndent(),
            )

        override fun readText(path: String): String = resources[path] ?: throw IllegalStateException("Missing resource '$path'")
    }
}
