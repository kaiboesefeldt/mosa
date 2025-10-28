package straightway.mosa.client.i18n

import js.intl.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class Translator(locales: List<Locale>) {
    private val translations = resolveTranslations(locales) ?: fallbackTranslations
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
        for (candidate in candidates) {
            val loaded = loadTranslations(candidate)
            if (loaded.isNotEmpty()) {
                return loaded
            }
        }

        return null
    }

    private companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val fallbackTranslations: Map<String, String> by lazy { loadTranslations("en") }
        private val resourceContext: dynamic = js("""require.context("./i18n", false, /\.json$/)""")

        @Suppress("RegExpRedundantEscape")
        private val placeholderRegex = Regex("\\{\\{([^{}]+)\\}\\}")

        private fun loadTranslations(tag: String): Map<String, String> {
            val resource = resolveResource(tag)
            return if (resource != null) resourceToMap(resource) else emptyMap()
        }

        private fun resolveResource(tag: String): dynamic? = try {
            resourceContext("./$tag.json")
        } catch (_: dynamic) {
            null
        }

        private fun resourceToMap(resource: dynamic): Map<String, String> {
            val element = toJsonElement(resource)
            val jsonObject = element.jsonObject
            return jsonObject.entries.associate { entry -> entry.key to entry.value.jsonPrimitive.content }
        }

        private fun toJsonElement(resource: dynamic): JsonElement {
            val serialized = JSON.stringify(resource)
            return json.parseToJsonElement(serialized)
        }
    }
}
