package straightway.mosa.legal.clearterms.document

data class TextBlockHints(
    val expectedContent: String?,
    val override: TextOverrideSeverity?,
    val inlineWithNext: Boolean? = null,
)
