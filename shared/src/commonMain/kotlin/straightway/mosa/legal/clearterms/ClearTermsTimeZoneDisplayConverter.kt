package straightway.mosa.legal.clearterms

interface ClearTermsTimeZoneDisplayConverter {
    fun convert(
        value: String,
        parsed: ClearTermsTimeValue,
    ): ClearTermsTimeDisplayValue?

    object NoOp : ClearTermsTimeZoneDisplayConverter {
        override fun convert(
            value: String,
            parsed: ClearTermsTimeValue,
        ): ClearTermsTimeDisplayValue? = null
    }
}

data class ClearTermsTimeDisplayValue(
    val year: Int,
    val month: Int,
    val day: Int,
    val time: String,
)
