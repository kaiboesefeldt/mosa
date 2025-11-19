package straightway.mosa.client.topic

import kotlinx.browser.window
import kotlin.js.JSON
import kotlin.js.json
import org.w3c.dom.Storage

interface TopicFilterStore {
    fun load(): Map<String, String>
    fun save(filters: Map<String, String>)
}

class BrowserTopicFilterStore(
    private val storageProvider: () -> Storage? = ::browserStorage,
) : TopicFilterStore {
    override fun load(): Map<String, String> {
        val raw = runCatching { storageProvider()?.getItem(key) }.getOrNull() ?: return emptyMap()
        return parse(raw)
    }

    override fun save(filters: Map<String, String>) {
        runCatching {
            val storage = storageProvider() ?: return
            storage.setItem(key, encode(filters))
        }
    }

    private companion object {
        const val key = "topic-filters"

        fun browserStorage(): Storage? =
            if (js("typeof window === 'undefined'") as Boolean) {
                null
            } else {
                runCatching { window.localStorage }.getOrNull()
            }

        private fun encode(filters: Map<String, String>): String {
            if (filters.isEmpty()) return "{}"
            val sanitized =
                filters
                    .filterKeys { it.isNotBlank() }
                    .filterValues { it.isNotBlank() }
                    .map { it.key to it.value }
                    .toTypedArray()
            return JSON.stringify(json(*sanitized))
        }

        private fun parse(raw: String): Map<String, String> {
            val parsed: dynamic = runCatching { JSON.parse<dynamic>(raw) }.getOrNull() ?: return emptyMap()
            val isObject = js("(typeof parsed === 'object') && parsed !== null && !Array.isArray(parsed)") as Boolean
            if (!isObject) return emptyMap()
            val entries = mutableMapOf<String, String>()
            val keys = (js("Object.keys(parsed)") as Array<String>?) ?: emptyArray()
            keys.forEach { title ->
                val value = parsed[title]
                val normalized = (value as? String)?.trim()
                if (!normalized.isNullOrEmpty() && title.isNotBlank()) {
                    entries[title] = normalized
                }
            }
            return entries.toMap()
        }
    }
}
