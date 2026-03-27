package straightway.mosa.legal.clearterms.render

data class RenderedLanguage(
    val languageTag: String,
    val text: String,
    val blocks: List<RenderedBlock> = emptyList(),
)
