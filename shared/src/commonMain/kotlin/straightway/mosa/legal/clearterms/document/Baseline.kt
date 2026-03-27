package straightway.mosa.legal.clearterms.document

data class Baseline(
    val id: String,
    val version: String,
    val textsByLanguage: Map<String, List<TextBlock>>,
)
