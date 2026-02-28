package straightway.mosa.legal.clearterms

data class ClearTermsRenderResult(
    val textsByLanguage: Map<String, String>,
    val blocksByLanguage: Map<String, List<ClearTermsRenderedBlock>> = emptyMap(),
)
