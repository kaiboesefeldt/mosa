package straightway.mosa.legal.clearterms

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ClearTermsLocaleRepository(
    private val resourceReader: ClearTermsResourceReader,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ClearTermsLocaleProvider {
    private val locales: Map<String, ClearTermsLocale> by lazy { loadLocales() }

    override fun resolve(languageTag: String): ClearTermsLocale {
        val primary = languageTag.substringBefore('-').substringBefore('_').lowercase()
        return locales[primary] ?: locales.getValue("en")
    }

    private fun loadLocales(): Map<String, ClearTermsLocale> {
        val indexText = resourceReader.readText("clearterms/locales/index.json")
        val languages = json.decodeFromString<List<String>>(indexText)
        return languages.associateWith { language ->
            val localeText = resourceReader.readText("clearterms/locales/$language.json")
            val definition = json.decodeFromString<LocaleDefinition>(localeText)
            definition.toLocale()
        }
    }

    @Serializable
    private data class LocaleDefinition(
        val language: String,
        val months: List<String>,
        val weekdays: Map<String, String>,
        val decimalSeparator: String,
        val groupingSeparator: String,
        val atWord: String,
        val patterns: PatternsDefinition,
    ) {
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

    @Serializable
    private data class PatternsDefinition(
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
}
