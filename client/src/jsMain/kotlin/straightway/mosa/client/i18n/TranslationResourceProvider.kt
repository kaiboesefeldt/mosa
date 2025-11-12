package straightway.mosa.client.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class TranslationResource(val tag: String, val entries: Map<String, String>)

interface TranslationResourceProvider {
    fun load(tag: String): TranslationResource?
    fun fallback(): TranslationResource
}

internal object TranslationResourceProviderFactory {
    fun create(): TranslationResourceProvider = WebpackTranslationResourceProvider(
        js("""require.context("./i18n", false, /\.json$/)"""),
        Json { ignoreUnknownKeys = true }
    )
}

internal class WebpackTranslationResourceProvider(
    private val resourceContext: dynamic,
    private val json: Json
) : TranslationResourceProvider {
    private val fallbackBundle by lazy { load("en") ?: error("Missing fallback translations for en") }

    override fun load(tag: String): TranslationResource? {
        val resource = resolve(tag) ?: return null
        return TranslationResource(tag, toMap(resource))
    }

    override fun fallback(): TranslationResource = fallbackBundle

    private fun resolve(tag: String): dynamic? = try {
        resourceContext("./$tag.json")
    } catch (_: dynamic) {
        null
    }

    private fun toMap(resource: dynamic): Map<String, String> {
        val element = json.parseToJsonElement(JSON.stringify(resource))
        return element.jsonObject.entries.associate { entry ->
            entry.key to entry.value.jsonPrimitive.content
        }
    }
}
