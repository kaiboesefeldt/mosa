package straightway.mosa.client.browser

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import straightway.mosa.client.util.toByteArray
import kotlin.js.Promise
import kotlin.js.json

class SubtleCrypto(private val wrapped: dynamic) {
    suspend fun sign(algo: String, privateKey: CryptoKey, signingBytes: ByteArray): ByteArray =
        wrapped
            .sign(algo, privateKey, signingBytes)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()
            .toByteArray()

    suspend fun exportKey(format: String, key: CryptoKey): ByteArray =
        wrapped
            .exportKey(format, key)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()
            .toByteArray()

    suspend fun verify(algo: String, publicKey: CryptoKey, signature: ByteArray, data: ByteArray): Boolean =
        wrapped
            .verify(algo, publicKey, signature, data)
            .unsafeCast<Promise<Boolean>>()
            .await()

    suspend fun importKey(
        format: String,
        keyData: ByteArray,
        algorithm: KeyAlgorithm,
        exportable: Boolean,
        usages: Array<KeyUsage>,
    ): CryptoKey =
        wrapped
            .importKey(format, keyData, algorithm.descriptor, exportable, usages.toJsValues())
            .unsafeCast<Promise<CryptoKey>>()
            .await()

    suspend fun generateKey(algo: KeyAlgorithm, exportable: Boolean, usages: Array<KeyUsage>): CryptoKeyPair =
        wrapped
            .generateKey(algo.descriptor, exportable, usages.toJsValues())
            .unsafeCast<Promise<CryptoKeyPair>>()
            .await()

    suspend fun digest(algo: String, data: ByteArray): ByteArray =
        wrapped
            .digest(algo, data)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()
            .toByteArray()

    suspend fun importPasswordKey(password: ByteArray): CryptoKey =
        wrapped
            .importKey("raw", password, js("{ name: 'PBKDF2' }"), false, arrayOf("deriveKey"))
            .unsafeCast<Promise<CryptoKey>>()
            .await()

    suspend fun deriveAesKey(baseKey: CryptoKey, salt: ByteArray, iterations: Int): CryptoKey =
        wrapped
            .deriveKey(
                json(
                    "name" to "PBKDF2",
                    "salt" to salt,
                    "iterations" to iterations,
                    "hash" to "SHA-256",
                ),
                baseKey,
                json("name" to "AES-GCM", "length" to 256),
                false,
                arrayOf("encrypt", "decrypt"),
            )
            .unsafeCast<Promise<CryptoKey>>()
            .await()

    suspend fun encryptAesGcm(key: CryptoKey, iv: ByteArray, data: ByteArray): ByteArray =
        wrapped
            .encrypt(json("name" to "AES-GCM", "iv" to iv), key, data)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()
            .toByteArray()

    suspend fun decryptAesGcm(key: CryptoKey, iv: ByteArray, data: ByteArray): ByteArray =
        wrapped
            .decrypt(json("name" to "AES-GCM", "iv" to iv), key, data)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()
            .toByteArray()
}

private fun Array<KeyUsage>.toJsValues(): Array<String> = map(KeyUsage::value).toTypedArray()
