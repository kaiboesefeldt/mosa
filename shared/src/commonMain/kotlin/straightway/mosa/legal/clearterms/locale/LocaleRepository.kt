package straightway.mosa.legal.clearterms.locale

import kotlinx.serialization.json.Json
import straightway.mosa.legal.clearterms.locale.Locale
import straightway.mosa.legal.clearterms.locale.LocaleProvider
import straightway.mosa.legal.clearterms.spi.ResourceReader

class LocaleRepository(
    resourceReader: ResourceReader,
    json: Json = Json { ignoreUnknownKeys = true },
) : LocaleProvider {
    private val localeLoader = LocaleLoader(resourceReader, json)
    private val locales: Map<String, Locale> by lazy { localeLoader.loadAll() }

    override fun resolve(languageTag: String): Locale {
        val primary = languageTag.substringBefore('-').substringBefore('_').lowercase()
        return locales[primary] ?: locales.getValue("en")
    }
}
