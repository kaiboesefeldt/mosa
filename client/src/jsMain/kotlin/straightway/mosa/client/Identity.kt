package straightway.mosa.client

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import straightway.mosa.client.Base64.toBase64Url
import kotlin.js.Promise

private val subtle by lazy {
    window.asDynamic().crypto?.subtle
    ?: error("WebCrypto nicht verfügbar")
}


external interface CryptoKey

external interface CryptoKeyPair {
    val privateKey: CryptoKey
    val publicKey: CryptoKey
}

suspend fun CryptoKeyPair.pubRaw(): Uint8Array {
    val pubBuf: ArrayBuffer = subtle
        .exportKey("raw", publicKey)
        .unsafeCast<Promise<ArrayBuffer>>()
        .await()

    return Uint8Array(pubBuf)
}

suspend fun CryptoKeyPair.privPkcs8(): Uint8Array {
    val privBuf: ArrayBuffer = subtle
        .exportKey("pkcs8", privateKey)
        .unsafeCast<Promise<ArrayBuffer>>()
        .await()

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

        val keyPair = subtle
            .generateKey(alg, true, usages)
            .unsafeCast<Promise<CryptoKeyPair>>()
            .await()

//        val pubBuf: ArrayBuffer = subtle
//            .exportKey("raw", keyPair.publicKey)
//            .unsafeCast<Promise<ArrayBuffer>>()
//            .await()

        val digBuf: ArrayBuffer = subtle
            .digest("SHA-256", keyPair.pubRaw())
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()

        val gid = Uint8Array(digBuf).toBase64Url()

        return Identity(gid, keyPair)
    }
}
