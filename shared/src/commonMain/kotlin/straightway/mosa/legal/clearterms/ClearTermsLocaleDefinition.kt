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
    val duration: DurationTexts = DurationTexts(),
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

    @Serializable
    data class DurationTexts(
        val infinite: String = "infinite",
        val units: Map<String, UnitTexts> = defaultDurationUnits,
    ) {
        @Serializable
        data class UnitTexts(
            val singular: String,
            val plural: String,
        )

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
            duration =
                ClearTermsLocale.DurationTexts(
                    infinite = duration.infinite,
                    units =
                        duration.units.mapValues { (_, value) ->
                            ClearTermsLocale.DurationTexts.UnitTexts(
                                singular = value.singular,
                                plural = value.plural,
                            )
                        },
                ),
        )
}
