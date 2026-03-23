package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.placeholder.ClearTermsPlaceholderType

data class ClearTermsValue(
    val type: ClearTermsPlaceholderType,
    val value: Any?,
)
