package straightway.mosa.client

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.koin.core.component.get
import straightway.mosa.client.Base64.toBase64Url

external interface CryptoKey

external interface CryptoKeyPair {
    val privateKey: CryptoKey
    val publicKey: CryptoKey
}

suspend fun CryptoKeyPair.pubRaw(): Uint8Array {
    val pubBuf: ArrayBuffer = KoinHolder.get<SubtleCrypto>().exportKey("raw", publicKey)
    return Uint8Array(pubBuf)
}

suspend fun CryptoKeyPair.privPkcs8(): Uint8Array {
    val privBuf: ArrayBuffer = KoinHolder.get<SubtleCrypto>().exportKey("pkcs8", privateKey)
    return Uint8Array(privBuf)
}

data class Identity(
    val gid: String,
    val keyPair: CryptoKeyPair
)

class IdentityGenerator {

    suspend fun generate(): Identity {
        val alg = js("({ name: 'Ed25519' })")
        val usages = arrayOf("sign", "verify")

        with(KoinHolder.get<SubtleCrypto>()) {
            val keyPair = generateKey(alg, true, usages)
            val digBuf: ArrayBuffer = digest("SHA-256", keyPair.pubRaw())
            val gid = Uint8Array(digBuf).toBase64Url()
            return Identity(gid, keyPair)
        }
    }
}
