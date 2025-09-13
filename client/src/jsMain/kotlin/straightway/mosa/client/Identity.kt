package straightway.mosa.client

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import straightway.mosa.client.Base64.toBase64Url
import kotlin.js.Promise

data class Identity(
    val gid: String,
    val pub: Uint8Array,
    val priv: Uint8Array,
)

class IdentityGenerator {

    suspend fun generate(): Identity {
        val subtle = window.asDynamic().crypto?.subtle
            ?: error("WebCrypto nicht verfügbar")

        val alg = js("({ name: 'Ed25519' })")
        val usages = arrayOf("sign", "verify")

        val keyPair = subtle
            .generateKey(alg, true, usages)
            .unsafeCast<Promise<dynamic>>()
            .await()

        val pubBuf: ArrayBuffer = subtle
            .exportKey("raw", keyPair.publicKey)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()

        val pubBytes = Uint8Array(pubBuf)

        val privBuf: ArrayBuffer = subtle
            .exportKey("pkcs8", keyPair.privateKey)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()

        val privBytes = Uint8Array(privBuf)

        val digBuf: ArrayBuffer = subtle
            .digest("SHA-256", pubBuf)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()

        val gid = Uint8Array(digBuf).toBase64Url()

        return Identity(gid, pubBytes, privBytes)
    }
}
