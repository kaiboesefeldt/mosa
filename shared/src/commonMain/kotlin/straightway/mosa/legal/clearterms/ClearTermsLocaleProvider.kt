package straightway.mosa.legal.clearterms

fun interface ClearTermsLocaleProvider {
    fun resolve(languageTag: String): ClearTermsLocale
}
