package straightway.mosa.legal.clearterms.document

data class TextBlock(
    val id: String,
    val text: String?,
    val hints: TextBlockHints? = null,
)
