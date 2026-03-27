package straightway.mosa.legal.clearterms.value

sealed class NumberFormat {
    data object Auto : NumberFormat()

    data object Integer : NumberFormat()

    data class Decimal(
        val scale: Int,
    ) : NumberFormat()
}
