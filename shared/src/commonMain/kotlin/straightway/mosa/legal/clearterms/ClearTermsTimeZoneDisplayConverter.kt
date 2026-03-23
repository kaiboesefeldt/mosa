package straightway.mosa.legal.clearterms

fun interface ClearTermsTimeZoneDisplayConverter {
    fun convert(
        value: String,
        parsed: ClearTermsTimeValue,
    ): ClearTermsTimeDisplayValue?

    companion object {
        val NoOp = ClearTermsTimeZoneDisplayConverter { _, _ -> null }
    }
}

data class ClearTermsTimeDisplayValue(
    val year: Int,
    val month: Int,
    val day: Int,
    val time: String,
)
