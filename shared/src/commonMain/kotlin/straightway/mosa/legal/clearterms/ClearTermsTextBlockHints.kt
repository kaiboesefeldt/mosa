package straightway.mosa.legal.clearterms

data class ClearTermsTextBlockHints(
    val expectedContent: String?,
    val override: ClearTermsTextOverrideSeverity?,
    val inlineWithNext: Boolean? = null,
)
