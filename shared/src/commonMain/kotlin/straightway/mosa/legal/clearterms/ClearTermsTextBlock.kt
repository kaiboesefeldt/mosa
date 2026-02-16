package straightway.mosa.legal.clearterms

data class ClearTermsTextBlock(
    val id: String,
    val text: String?,
    val hints: ClearTermsTextBlockHints? = null,
)
