package straightway.mosa.client

import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

data class Identity(
    val gid: String,
    val publicRaw: Uint8Array,
    val publicB64: String,
    val privatePkcs8: Uint8Array,
    val privateB64: String,
)

class IdentityGenerator {
    fun generate(): Promise<Identity> {
        val subtle = window.asDynamic().crypto?.subtle
        if (subtle == null) return Promise.reject(Throwable("WebCrypto nicht verfügbar"))

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
                                        val gid = toBase64Url(Uint8Array(digBuf))
                                        val pubB64 = toBase64Url(pubBytes)
                                        val privB64 = toBase64Url(privBytes)
                                        Identity(gid, pubBytes, pubB64, privBytes, privB64)
                                    }
                            }
                    }
            }
    }

    private fun toBase64Url(bytes: Uint8Array): String {
        val len = bytes.length
        val sb = StringBuilder(len)
        var i = 0
        while (i < len) {
            val v = (bytes.asDynamic()[i] as Int)
            sb.append(v.toChar())
            i++
        }
        val binary = sb.toString()
        val b64 = window.btoa(binary)
        return b64.replace('+', '-').replace('/', '_').trimEnd('=')
    }
}
