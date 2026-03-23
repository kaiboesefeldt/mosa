package straightway.mosa.legal.clearterms

fun interface ClearTermsHashProvider {
    suspend fun sha256(data: ByteArray): ByteArray
}
