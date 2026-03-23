package straightway.mosa.legal.clearterms.placeholder

import straightway.mosa.legal.clearterms.ClearTermsNumberOptions

data class ClearTermsPlaceholder(
    val type: ClearTermsPlaceholderType,
    val key: String,
    val fallback: String?,
    val numberOptions: ClearTermsNumberOptions?,
)
