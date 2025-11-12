package straightway.mosa.client.i18n

import js.intl.Locale

class Translator(
    locales: List<Locale>,
    private val resourceProvider: TranslationResourceProvider = TranslationResourceProviderFactory.create()
) {
    private val translations = resolveTranslations(locales) ?: resourceProvider.fallback().entries
    private var locale: Locale? = null

    fun translate(key: String, replacements: Map<String, String> = emptyMap()): String {
        val template = translations[key] ?: throw RuntimeException(
            "No translation for key '$key' in locale ${locale ?: Locale("en")}"
        )
        return applyReplacements(template, replacements)
    }

    private fun applyReplacements(template: String, replacements: Map<String, String>): String {
        if (replacements.isEmpty()) return template
        return placeholderRegex.replace(template) { match ->
            val key = match.groupValues[1].trim()
            replacements[key] ?: match.value
        }
    }

    private fun resolveTranslations(languages: List<Locale>): Map<String, String>? =
        languages.firstNotNullOfOrNull { loc -> resolveTranslation(loc)?.also { locale = loc } }

    private fun resolveTranslation(language: Locale): Map<String, String>? {
        val candidates = listOf(language.toString().lowercase(), language.language).distinct()
        return candidates.firstNotNullOfOrNull { tag -> resourceProvider.load(tag)?.entries }
    }

    private companion object {
        @Suppress("RegExpRedundantEscape")
        private val placeholderRegex = Regex("\\{\\{([^{}]+)\\}\\}")
    }
}
