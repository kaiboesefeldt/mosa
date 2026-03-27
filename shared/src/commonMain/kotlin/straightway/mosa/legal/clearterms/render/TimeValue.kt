package straightway.mosa.legal.clearterms.render

data class TimeValue(
    val year: Int?,
    val month: Int?,
    val day: Int?,
    val time: String?,
    val isOverflow: Boolean,
)
