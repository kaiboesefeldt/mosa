package straightway.mosa.legal.clearterms.value

import straightway.mosa.legal.clearterms.placeholder.PlaceholderType

data class Value(
    val type: PlaceholderType,
    val value: Any?,
)
