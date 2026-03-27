package straightway.mosa.legal.clearterms.spi

fun interface TextEncoder {
    fun encode(value: String): ByteArray
}
