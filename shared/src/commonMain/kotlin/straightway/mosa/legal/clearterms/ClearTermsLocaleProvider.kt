package straightway.mosa.legal.clearterms

interface ClearTermsLocaleProvider {
    fun resolve(languageTag: String): ClearTermsLocale
}
