package straightway.mosa.legal.clearterms.locale

import kotlinx.serialization.json.Json
import straightway.mosa.legal.clearterms.locale.Locale
import straightway.mosa.legal.clearterms.spi.ResourceReader

class LocaleLoader(
    private val resourceReader: ResourceReader,
    private val json: Json,
) {
    fun loadAll(): Map<String, Locale> =
        loadLanguageCodes().associateWith { language ->
            loadLocale(language)
        }

    private fun loadLanguageCodes(): List<String> {
        val indexText = resourceReader.readText("clearterms/locales/index.json")
        return json.decodeFromString(indexText)
    }

    private fun loadLocale(language: String): Locale {
        val localeText = resourceReader.readText("clearterms/locales/$language.json")
        return json.decodeFromString(localeText)
    }
}
