package straightway.mosa.client

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import straightway.mosa.client.Base64.toBase64Url

class IdentityGenerator(private val crypto: Crypto, private val subtleCrypto: SubtleCrypto) {

    suspend fun generate(): Identity {
        val alg = js("({ name: 'Ed25519' })")
        val usages = arrayOf("sign", "verify")

        with(subtleCrypto) {
            val keyPair = generateKey(alg, true, usages)
            val digBuf: ArrayBuffer = digest("SHA-256", crypto.pubRawOf(keyPair))
            val gid = Uint8Array(digBuf).toBase64Url()
            return Identity(gid, keyPair)
        }
    }
}