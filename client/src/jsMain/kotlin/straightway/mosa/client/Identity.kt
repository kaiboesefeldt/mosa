package straightway.mosa.client

import kotlinx.browser.window
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
    fun generate(): Promise<Identity> {
        val subtle = window.asDynamic().crypto?.subtle
            ?: return Promise.reject(Throwable("WebCrypto nicht verfügbar"))

        val alg = js("({ name: 'Ed25519' })")
        val usages = arrayOf("sign", "verify")
        return subtle
            .generateKey(alg, true, usages)
            .then { keyPair: dynamic ->
                subtle
                    .exportKey("raw", keyPair.publicKey)
                    .unsafeCast<Promise<ArrayBuffer>>()
                    .then { pubBuf: ArrayBuffer ->
                        val pubBytes = Uint8Array(pubBuf)
                        subtle
                            .exportKey("pkcs8", keyPair.privateKey)
                            .unsafeCast<Promise<ArrayBuffer>>()
                            .then { privBuf: ArrayBuffer ->
                                val privBytes = Uint8Array(privBuf)
                                subtle
                                    .digest("SHA-256", pubBuf)
                                    .unsafeCast<Promise<ArrayBuffer>>()
                                    .then { digBuf: ArrayBuffer ->
                                        val gid = Uint8Array(digBuf).toBase64Url()
                                        Identity(gid, pubBytes, privBytes)
                                    }
                            }
                    }
            }
    }
}
