package straightway.mosa.legal.clearterms

sealed class ClearTermsNumberFormat {
    data object Auto : ClearTermsNumberFormat()

    data object Integer : ClearTermsNumberFormat()

    data class Decimal(
        val scale: Int,
    ) : ClearTermsNumberFormat()
}
