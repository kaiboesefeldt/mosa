package straightway.mosa.client.identity

import straightway.mosa.client.browser.AlgorithmIdentifiers
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.util.Base64.toBase64Url

class IdentityGenerator(
    private val cryptoExport: CryptoExport,
    private val subtleCrypto: SubtleCrypto,
) : IdentityProvider {
    override suspend fun generate(): Identity {
        val alg = AlgorithmIdentifiers.ed25519
        val usages = arrayOf("sign", "verify")

        with(subtleCrypto) {
            val keyPair = generateKey(alg, true, usages)
            val digestBytes = digest("SHA-256", cryptoExport.pubRawOf(keyPair))
            val gid = digestBytes.toBase64Url()
            return Identity(gid, keyPair)
        }
    }
}
