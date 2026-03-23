package straightway.mosa.legal.clearterms

data class ClearTermsRenderedLanguage(
    val languageTag: String,
    val text: String,
    val blocks: List<ClearTermsRenderedBlock> = emptyList(),
)
