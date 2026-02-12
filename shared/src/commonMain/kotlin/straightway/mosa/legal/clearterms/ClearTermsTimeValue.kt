package straightway.mosa.legal.clearterms

data class ClearTermsTimeValue(
    val year: Int?,
    val month: Int?,
    val day: Int?,
    val time: String?,
    val isOverflow: Boolean,
)
