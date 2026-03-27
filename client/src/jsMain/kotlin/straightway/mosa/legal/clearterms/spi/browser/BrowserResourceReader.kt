package straightway.mosa.legal.clearterms.spi.browser

import straightway.mosa.legal.clearterms.spi.ResourceReader

class BrowserResourceReader : ResourceReader {
    override fun readText(path: String): String =
        when (path) {
            "clearterms/locales/index.json" -> JSON.stringify(localeIndex)
            "clearterms/locales/en.json" -> JSON.stringify(localeEn)
            "clearterms/locales/de.json" -> JSON.stringify(localeDe)
            else -> throw IllegalStateException("Missing resource '$path'")
        }
}

@JsModule("./clearterms/locales/index.json")
@JsNonModule
private external val localeIndex: Array<String>

@JsModule("./clearterms/locales/en.json")
@JsNonModule
private external val localeEn: dynamic

@JsModule("./clearterms/locales/de.json")
@JsNonModule
private external val localeDe: dynamic
