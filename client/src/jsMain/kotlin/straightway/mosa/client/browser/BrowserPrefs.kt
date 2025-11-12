package straightway.mosa.client.browser

import js.array.ReadonlyArray
import js.intl.Locale
import kotlin.collections.buildList
import kotlin.js.unsafeCast
import web.navigator.Navigator
import web.navigator.navigator

class BrowserPrefs(
    private val navigatorProvider: () -> Navigator? = ::currentNavigator
) {
    val languages: List<Locale>
        get() = navigatorProvider()
            ?.preferredLanguageTags()
            ?.map(::Locale)
            ?: emptyList()

    private fun Navigator.preferredLanguageTags(): List<String> {
        val preferences = unsafeCast<LanguageAwareNavigator>()
        return buildList {
            addAll(preferences.languages.collectLanguageTags())
            preferences.language
                ?.takeUnless(String::isBlank)
                ?.let(::add)
        }.distinct()
    }

    private fun ReadonlyArray<String>?.collectLanguageTags(): List<String> =
        this?.let { values ->
            buildList {
                repeat(values.length) { index ->
                    val tag = values[index]
                    if (tag.isNotBlank()) add(tag)
                }
            }
        } ?: emptyList()

    private companion object {
        @Suppress("UnsafeCastFromDynamic")
        private fun currentNavigator(): Navigator? =
            if (js("typeof navigator === 'undefined'") as Boolean) {
                null
            } else {
                navigator
            }
    }
}

private external interface LanguageAwareNavigator {
    val language: String?
    val languages: ReadonlyArray<String>?
}
