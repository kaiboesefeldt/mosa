package straightway.mosa.client.identity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import straightway.mosa.client.browser.AlgorithmIdentifiers
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.util.Base64
import straightway.mosa.client.util.Base64.toBase64Url
import kotlin.js.json
import kotlin.js.unsafeCast

class IdentityTransferCodec(
    private val cryptoExport: CryptoExport,
    private val subtleCrypto: SubtleCrypto,
    private val jsonCodec: Json,
) {
    suspend fun encode(identity: Identity): String {
        val publicKey = cryptoExport.pubRawOf(identity.keyPair).toBase64Url()
        val privateKey = cryptoExport.privPkcs8Of(identity.keyPair).toBase64Url()
        val payload = IdentityTransferPayload(identity.gid, publicKey, privateKey)
        return jsonCodec.encodeToString(IdentityTransferPayload.serializer(), payload)
    }

    suspend fun decode(serialized: String): Identity {
        val payload = jsonCodec.decodeFromString(IdentityTransferPayload.serializer(), serialized)
        val publicKeyBytes = Base64.fromBase64Url(payload.publicKey)
        val privateKeyBytes = Base64.fromBase64Url(payload.privateKey)
        val digest = subtleCrypto.digest("SHA-256", publicKeyBytes).toBase64Url()
        require(digest == payload.gid) { "Public key digest mismatch" }
        val algorithm = AlgorithmIdentifiers.ed25519
        val publicKey = subtleCrypto.importKey("raw", publicKeyBytes, algorithm, true, arrayOf("verify"))
        val privateKey = subtleCrypto.importKey("pkcs8", privateKeyBytes, algorithm, true, arrayOf("sign"))
        val keyPair =
            json(
                "publicKey" to publicKey,
                "privateKey" to privateKey,
            ).unsafeCast<CryptoKeyPair>()
        return Identity(digest, keyPair)
    }
}

@Serializable
data class IdentityTransferPayload(
    val gid: String,
    val publicKey: String,
    val privateKey: String,
)
