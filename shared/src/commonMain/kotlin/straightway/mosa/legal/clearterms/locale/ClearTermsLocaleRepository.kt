package straightway.mosa.legal.clearterms.locale

import kotlinx.serialization.json.Json
import straightway.mosa.legal.clearterms.ClearTermsLocale
import straightway.mosa.legal.clearterms.ClearTermsLocaleProvider
import straightway.mosa.legal.clearterms.ClearTermsResourceReader

class ClearTermsLocaleRepository(
    resourceReader: ClearTermsResourceReader,
    json: Json = Json { ignoreUnknownKeys = true },
) : ClearTermsLocaleProvider {
    private val localeLoader = ClearTermsLocaleLoader(resourceReader, json)
    private val locales: Map<String, ClearTermsLocale> by lazy { localeLoader.loadAll() }

    override fun resolve(languageTag: String): ClearTermsLocale {
        val primary = languageTag.substringBefore('-').substringBefore('_').lowercase()
        return locales[primary] ?: locales.getValue("en")
    }
}
