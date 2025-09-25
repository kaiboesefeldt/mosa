package straightway.mosa.client.identity

import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.client.browser.SubtleCrypto

class IdentityGenerator(private val cryptoExport: CryptoExport, private val subtleCrypto: SubtleCrypto) {

    suspend fun generate(): Identity {
        val alg = js("({ name: 'Ed25519' })")
        val usages = arrayOf("sign", "verify")

        with(subtleCrypto) {
            val keyPair = generateKey(alg, true, usages)
            val digestBytes = digest("SHA-256", cryptoExport.pubRawOf(keyPair))
            val gid = digestBytes.toBase64Url()
            return Identity(gid, keyPair)
        }
    }
}
