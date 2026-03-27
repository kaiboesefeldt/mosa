package straightway.mosa.legal.clearterms.placeholder

import straightway.mosa.legal.clearterms.value.NumberOptions

data class Placeholder(
    val type: PlaceholderType,
    val key: String,
    val fallback: String?,
    val numberOptions: NumberOptions?,
)
