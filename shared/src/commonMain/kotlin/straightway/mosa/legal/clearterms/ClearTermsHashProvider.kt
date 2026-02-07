package straightway.mosa.legal.clearterms

interface ClearTermsHashProvider {
    suspend fun sha256(data: ByteArray): ByteArray
}
