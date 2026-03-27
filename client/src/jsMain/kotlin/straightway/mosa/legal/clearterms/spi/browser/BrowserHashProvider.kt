package straightway.mosa.legal.clearterms.spi.browser

import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.legal.clearterms.spi.HashProvider

class BrowserHashProvider(private val subtleCrypto: SubtleCrypto) : HashProvider {
    override suspend fun sha256(data: ByteArray): ByteArray = subtleCrypto.digest("SHA-256", data)
}
