package straightway.mosa.client.identity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.aux.TimeService
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.util.textEncode
import straightway.mosa.client.util.Base64.toBase64Url

class MisJwtService(
    private val timeService: TimeService,
    private val cryptoExport: CryptoExport,
    private val subtleCrypto: SubtleCrypto
) {

    suspend fun buildMisJwt(
        identity: Identity,
        cert: PodCertificate,
        lifetimeSeconds: Long = 24L * 60L * 60L,
    ): String {
        val header = JwtHeader(alg = "ed25519", typ = "JWT")
        val nowSeconds = timeService.now

        val payload =
            JwtPayload(
                pub = identity.publicKeyBase64 ?: cryptoExport.pubRawOf(identity.keyPair).toBase64Url(),
                cert = cert,
                exp = (nowSeconds + lifetimeSeconds).toInt(),
                mvs = "1",
            )

        val headerB64 = encodeJsonToBase64Url(header)
        val payloadB64 = encodeJsonToBase64Url(payload)
        val signingInput = "$headerB64.$payloadB64"
        val signingBytes = textEncode(signingInput)

        val signatureBytes = subtleCrypto.sign("Ed25519", identity.keyPair.privateKey, signingBytes)
        val signatureB64 = signatureBytes.toBase64Url()

        return "$headerB64.$payloadB64.$signatureB64"
    }

    @Serializable
    private data class JwtHeader(
        val alg: String,
        val typ: String,
    )

    @Serializable
    private data class JwtPayload(
        val pub: String,
        val cert: PodCertificate,
        val exp: Int,
        val mvs: String,
    )

    private val jsonCodec = Json { encodeDefaults = true }

    private fun encodeJsonToBase64Url(obj: JwtHeader): String =
        textEncode(jsonCodec.encodeToString(obj)).toBase64Url()

    private fun encodeJsonToBase64Url(obj: JwtPayload): String =
        textEncode(jsonCodec.encodeToString(obj)).toBase64Url()
}
