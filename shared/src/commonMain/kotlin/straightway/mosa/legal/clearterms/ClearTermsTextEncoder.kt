package straightway.mosa.legal.clearterms

fun interface ClearTermsTextEncoder {
    fun encode(value: String): ByteArray
}
