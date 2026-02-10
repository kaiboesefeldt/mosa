package straightway.mosa.legal.clearterms

data class ClearTermsLocale(
    val language: String,
    val months: List<String>,
    val weekdays: Map<String, String>,
    val decimalSeparator: Char,
    val groupingSeparator: Char,
    val atWord: String,
    val patterns: Patterns,
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
