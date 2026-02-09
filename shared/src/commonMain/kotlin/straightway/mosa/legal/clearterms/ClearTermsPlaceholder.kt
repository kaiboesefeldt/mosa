package straightway.mosa.legal.clearterms

data class ClearTermsPlaceholder(
    val type: ClearTermsPlaceholderType,
    val key: String,
    val fallback: String?,
    val numberOptions: ClearTermsNumberOptions?,
)
