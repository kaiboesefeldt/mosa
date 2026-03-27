package straightway.mosa.legal.clearterms.spi

fun interface HashProvider {
    suspend fun sha256(data: ByteArray): ByteArray
}
