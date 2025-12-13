package straightway.mosa.client.identity

import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.KeyAlgorithm
import straightway.mosa.client.browser.KeyUsage
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.util.Base64
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.client.util.textEncode
import kotlin.js.json
import kotlin.js.unsafeCast
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

open class IdentityProtector(
    private val subtle: SubtleCrypto,
    private val export: CryptoExport,
    private val jsonCodec: Json = Json,
    private val iterations: Int = defaultIterations,
) {
    open suspend fun protect(identity: Identity, password: String): Identity {
        require(password.isNotEmpty()) { "Password required" }
        require(identity.protectedExport == null) { "Identity already protected" }
        val payload = payload(identity)
        val serialized = encrypt(payload, password)
        val protectedKeyPair = importKeyPair(payload, privateExportable = false)
        return identity.copy(
            keyPair = protectedKeyPair,
            protectedExport = serialized,
            publicKeyBase64 = payload.publicKey,
        )
    }

    open suspend fun import(serialized: String, password: String): Identity {
        require(password.isNotEmpty()) { "Password required" }
        val payload = decrypt(serialized, password)
        val keyPair = importKeyPair(payload, privateExportable = false)
        return Identity(payload.gid, keyPair, serialized, payload.publicKey)
    }

    private suspend fun payload(identity: Identity): IdentityTransferPayload {
        val publicKey = identity.publicKeyBase64 ?: export.pubRawOf(identity.keyPair).toBase64Url()
        val privateKey = export.privPkcs8Of(identity.keyPair).toBase64Url()
        return IdentityTransferPayload(identity.gid, publicKey, privateKey)
    }

    private suspend fun encrypt(payload: IdentityTransferPayload, password: String): String {
        val salt = randomBytes(16)
        val iv = randomBytes(12)
        val passwordKey = subtle.importPasswordKey(textEncode(password))
        val aesKey = subtle.deriveAesKey(passwordKey, salt, iterations)
        val plaintext = jsonCodec.encodeToString(IdentityTransferPayload.serializer(), payload).encodeToByteArray()
        val encrypted = subtle.encryptAesGcm(aesKey, iv, plaintext)
        val envelope = ProtectedIdentityEnvelope(
            version = 1,
            salt = salt.toBase64Url(),
            iv = iv.toBase64Url(),
            iterations = iterations,
            cipherText = encrypted.toBase64Url(),
        )
        return jsonCodec.encodeToString(ProtectedIdentityEnvelope.serializer(), envelope)
    }

    private suspend fun decrypt(serialized: String, password: String): IdentityTransferPayload {
        val envelope = jsonCodec.decodeFromString(ProtectedIdentityEnvelope.serializer(), serialized)
        require(envelope.version == 1) { "Unsupported identity format" }
        val passwordKey = subtle.importPasswordKey(textEncode(password))
        val aesKey = subtle.deriveAesKey(passwordKey, Base64.fromBase64Url(envelope.salt), envelope.iterations)
        val decrypted = subtle.decryptAesGcm(
            aesKey,
            Base64.fromBase64Url(envelope.iv),
            Base64.fromBase64Url(envelope.cipherText),
        )
        val payload = jsonCodec.decodeFromString(IdentityTransferPayload.serializer(), decrypted.decodeToString())
        validate(payload)
        return payload
    }

    private suspend fun validate(payload: IdentityTransferPayload) {
        val publicKeyBytes = Base64.fromBase64Url(payload.publicKey)
        val digest = subtle.digest("SHA-256", publicKeyBytes).toBase64Url()
        require(digest == payload.gid) { "Public key digest mismatch" }
    }

    private suspend fun importKeyPair(payload: IdentityTransferPayload, privateExportable: Boolean): CryptoKeyPair {
        val publicKeyBytes = Base64.fromBase64Url(payload.publicKey)
        val privateKeyBytes = Base64.fromBase64Url(payload.privateKey)
        val algorithm = KeyAlgorithm.Ed25519
        val publicKey = subtle.importKey("raw", publicKeyBytes, algorithm, true, arrayOf(KeyUsage.Verify))
        val privateKey = subtle.importKey("pkcs8", privateKeyBytes, algorithm, privateExportable, arrayOf(KeyUsage.Sign))
        return json(
            "publicKey" to publicKey,
            "privateKey" to privateKey,
        ).unsafeCast<CryptoKeyPair>()
    }

    private fun randomBytes(length: Int): ByteArray {
        val array = Uint8Array(length)
        window.asDynamic().crypto?.getRandomValues(array) ?: error("Crypto random not available")
        val result = ByteArray(length)
        for (index in 0 until length) {
            result[index] = array[index]
        }
        return result
    }

    companion object {
        const val defaultIterations = 210_000
    }
}

@Serializable
private data class ProtectedIdentityEnvelope(
    val version: Int,
    val salt: String,
    val iv: String,
    val iterations: Int,
    val cipherText: String,
)
