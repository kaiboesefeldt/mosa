package straightway.mosa.legal.clearterms

data class ClearTermsBaseline(
    val id: String,
    val version: String,
    val textsByLanguage: Map<String, List<ClearTermsTextBlock>>,
)
