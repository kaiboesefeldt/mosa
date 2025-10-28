package straightway.mosa.client.browser

import js.intl.Locale
import kotlin.collections.buildList
import kotlin.js.unsafeCast

class BrowserPrefs(
    private val navigatorProvider: () -> dynamic = { js("typeof navigator === 'undefined' ? undefined : navigator") }
) {
    val languages: List<Locale>
        get() {
            val navigator = navigatorProvider()!!
            val tags = buildList {
                addAll(readLanguageArray(navigator))
                val primary = navigator.language as? String
                if (!primary.isNullOrBlank()) add(primary)
            }.distinct()
            return tags.map(::Locale)
        }

    private fun readLanguageArray(navigator: dynamic): List<String> {
        val source = navigator.languages ?: return emptyList()
        val isArray = js("Array.isArray(source)") as Boolean
        if (!isArray) return emptyList()
        return source.unsafeCast<Array<String>>().mapNotNull { it.takeIf(String::isNotBlank) }
    }
}
