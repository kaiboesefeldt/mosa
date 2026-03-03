package straightway.mosa.legal.clearterms

data class ClearTermsLocale(
    val language: String,
    val months: List<String>,
    val weekdays: Map<String, String>,
    val decimalSeparator: Char,
    val groupingSeparator: Char,
    val atWord: String,
    val patterns: Patterns,
    val duration: DurationTexts = DurationTexts(),
) {
    data class Patterns(
        val onFullDate: String,
        val inMonthYear: String,
        val onDayOfEveryMonthInYear: String,
        val inYear: String,
        val onMonthDayEveryYear: String,
        val everyDayInMonth: String,
        val onDayOfEveryMonth: String,
        val everyDay: String,
        val onLastDayOfMonth: String,
        val onLastDayOfMonthEveryYear: String,
        val onLastDayOfEveryMonthInYear: String,
        val onLastDayOfEveryMonth: String,
    )

    data class DurationTexts(
        val infinite: String = "infinite",
        val units: Map<String, UnitTexts> = defaultDurationUnits,
    ) {
        data class UnitTexts(
            val singular: String,
            val plural: String,
        )

        fun labelFor(
            unit: Char,
            singular: Boolean,
        ): String {
            val texts = units[unit.toString()] ?: throw ClearTermsValidationException("Unsupported duration unit '$unit'")
            return if (singular) texts.singular else texts.plural
        }

        private companion object {
            private val defaultDurationUnits: Map<String, UnitTexts> =
                mapOf(
                    "s" to UnitTexts("second", "seconds"),
                    "m" to UnitTexts("minute", "minutes"),
                    "h" to UnitTexts("hour", "hours"),
                    "d" to UnitTexts("day", "days"),
                    "w" to UnitTexts("week", "weeks"),
                    "y" to UnitTexts("year", "years"),
                )
        }
    }

    fun monthName(month: Int): String = months[month - 1]

    fun onFullDate(
        month: String,
        day: Int,
        year: Int,
    ): String = render(patterns.onFullDate, month, day, year)

    fun inMonthYear(
        month: String,
        year: Int,
    ): String = render(patterns.inMonthYear, month, null, year)

    fun onDayOfEveryMonthInYear(
        day: Int,
        year: Int,
    ): String = render(patterns.onDayOfEveryMonthInYear, null, day, year)

    fun inYear(year: Int): String = render(patterns.inYear, null, null, year)

    fun onMonthDayEveryYear(
        month: String,
        day: Int,
    ): String = render(patterns.onMonthDayEveryYear, month, day, null)

    fun everyDayInMonth(month: String): String = render(patterns.everyDayInMonth, month, null, null)

    fun onDayOfEveryMonth(day: Int): String = render(patterns.onDayOfEveryMonth, null, day, null)

    fun everyDay(): String = patterns.everyDay

    fun onLastDayOfMonth(
        month: String,
        year: Int,
    ): String = render(patterns.onLastDayOfMonth, month, null, year)

    fun onLastDayOfMonthEveryYear(month: String): String = render(patterns.onLastDayOfMonthEveryYear, month, null, null)

    fun onLastDayOfEveryMonthInYear(year: Int): String = render(patterns.onLastDayOfEveryMonthInYear, null, null, year)

    fun onLastDayOfEveryMonth(): String = patterns.onLastDayOfEveryMonth

    private fun render(
        pattern: String,
        month: String?,
        day: Int?,
        year: Int?,
    ): String {
        var text = pattern
        if (month != null) {
            text = text.replace("{month}", month)
        }
        if (day != null) {
            text = text.replace("{day}", day.toString())
        }
        if (year != null) {
            text = text.replace("{year}", year.toString())
        }
        return text
    }
}
