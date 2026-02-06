package straightway.mosa.legal.clearterms

import straightway.mosa.client.browser.SubtleCrypto

class ClearTermsBrowserHashProvider(private val subtleCrypto: SubtleCrypto) : ClearTermsHashProvider {
    override suspend fun sha256(data: ByteArray): ByteArray = subtleCrypto.digest("SHA-256", data)
}
