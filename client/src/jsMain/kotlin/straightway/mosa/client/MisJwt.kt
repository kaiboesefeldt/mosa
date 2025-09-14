package straightway.mosa.client

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import straightway.mosa.client.Base64.toBase64Url
import kotlin.js.JSON
import kotlin.js.json

suspend fun buildMisJwt(
    identity: Identity,
    cert: PodCertificate,
    lifetimeSeconds: Long = 24L * 60L * 60L,
): String {
    val header = json(
        "alg" to "ed25519",
        "typ" to "JWT",
    )
    val nowSeconds = kotlin.js.Date.now().toLong() / 1000L

    val payload = json(
        "pub" to identity.keyPair.pubRaw().toBase64Url(),
        "cert" to cert.toJson(),
        "exp" to (nowSeconds + lifetimeSeconds).toInt(),
        "mvs" to "1",
    )

    val headerB64 = encodeJsonToBase64Url(header)
    val payloadB64 = encodeJsonToBase64Url(payload)
    val signingInput = "$headerB64.$payloadB64"
    val signingBytes = textEncode(signingInput)

    val subtle = window.asDynamic().crypto.subtle
    val signatureBuf =
        subtle
            .sign("Ed25519", identity.keyPair.privateKey, signingBytes)
            .unsafeCast<kotlin.js.Promise<ArrayBuffer>>()
            .await()
    val signatureB64 = Uint8Array(signatureBuf).toBase64Url()

    return "$headerB64.$payloadB64.$signatureB64"
}

private fun encodeJsonToBase64Url(obj: dynamic): String =
    textEncode(JSON.stringify(obj)).toBase64Url()

private fun textEncode(s: String): Uint8Array = js("new TextEncoder().encode(s)") as Uint8Array
