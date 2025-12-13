package straightway.mosa.client.identity

import straightway.mosa.client.browser.KeyAlgorithm
import straightway.mosa.client.browser.KeyUsage
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.util.Base64.toBase64Url

class IdentityGenerator(
    private val cryptoExport: CryptoExport,
    private val subtleCrypto: SubtleCrypto,
) : IdentityProvider {
    override suspend fun generate(): Identity {
        val algorithm = KeyAlgorithm.Ed25519
        val usages = arrayOf(KeyUsage.Sign, KeyUsage.Verify)

        with(subtleCrypto) {
            val keyPair = generateKey(algorithm, true, usages)
            val publicKey = cryptoExport.pubRawOf(keyPair)
            val digestBytes = digest("SHA-256", publicKey)
            val gid = digestBytes.toBase64Url()
            return Identity(gid, keyPair, publicKeyBase64 = publicKey.toBase64Url())
        }
    }
}
