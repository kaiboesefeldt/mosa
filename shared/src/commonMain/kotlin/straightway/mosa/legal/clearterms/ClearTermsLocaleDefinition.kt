package straightway.mosa.legal.clearterms

import kotlinx.serialization.Serializable

@Serializable
data class ClearTermsLocaleDefinition(
    val language: String,
    val months: List<String>,
    val weekdays: Map<String, String>,
    val decimalSeparator: String,
    val groupingSeparator: String,
    val atWord: String,
    val patterns: Patterns,
) {
    @Serializable
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

    fun toLocale(): ClearTermsLocale =
        ClearTermsLocale(
            language = language,
            months = months,
            weekdays = weekdays,
            decimalSeparator = decimalSeparator.single(),
            groupingSeparator = groupingSeparator.single(),
            atWord = atWord,
            patterns =
                ClearTermsLocale.Patterns(
                    onFullDate = patterns.onFullDate,
                    inMonthYear = patterns.inMonthYear,
                    onDayOfEveryMonthInYear = patterns.onDayOfEveryMonthInYear,
                    inYear = patterns.inYear,
                    onMonthDayEveryYear = patterns.onMonthDayEveryYear,
                    everyDayInMonth = patterns.everyDayInMonth,
                    onDayOfEveryMonth = patterns.onDayOfEveryMonth,
                    everyDay = patterns.everyDay,
                    onLastDayOfMonth = patterns.onLastDayOfMonth,
                    onLastDayOfMonthEveryYear = patterns.onLastDayOfMonthEveryYear,
                    onLastDayOfEveryMonthInYear = patterns.onLastDayOfEveryMonthInYear,
                    onLastDayOfEveryMonth = patterns.onLastDayOfEveryMonth,
                ),
        )
}
