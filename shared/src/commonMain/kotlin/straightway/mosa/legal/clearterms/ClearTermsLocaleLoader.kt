package straightway.mosa.legal.clearterms

import kotlinx.serialization.json.Json

class ClearTermsLocaleLoader(
    private val resourceReader: ClearTermsResourceReader,
    private val json: Json,
) {
    fun loadAll(): Map<String, ClearTermsLocale> =
        loadLanguageCodes().associateWith { language ->
            loadLocale(language)
        }

    private fun loadLanguageCodes(): List<String> {
        val indexText = resourceReader.readText("clearterms/locales/index.json")
        return json.decodeFromString(indexText)
    }

    private fun loadLocale(language: String): ClearTermsLocale {
        val localeText = resourceReader.readText("clearterms/locales/$language.json")
        return json.decodeFromString(localeText)
    }
}
