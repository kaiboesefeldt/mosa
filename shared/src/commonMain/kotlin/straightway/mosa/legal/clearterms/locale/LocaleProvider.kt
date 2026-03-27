package straightway.mosa.legal.clearterms.locale

fun interface LocaleProvider {
    fun resolve(languageTag: String): Locale
}
