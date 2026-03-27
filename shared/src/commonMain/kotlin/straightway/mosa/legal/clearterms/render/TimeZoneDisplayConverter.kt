package straightway.mosa.legal.clearterms.render

fun interface TimeZoneDisplayConverter {
    fun convert(
        value: String,
        parsed: TimeValue,
    ): TimeDisplayValue?

    companion object {
        val NoOp = TimeZoneDisplayConverter { _, _ -> null }
    }
}

data class TimeDisplayValue(
    val year: Int,
    val month: Int,
    val day: Int,
    val time: String,
)
