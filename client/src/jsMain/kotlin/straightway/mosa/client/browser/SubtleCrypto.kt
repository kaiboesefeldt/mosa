package straightway.mosa.client.browser

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import straightway.mosa.client.util.toByteArray
import kotlin.js.Promise

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
}

private fun Array<KeyUsage>.toJsValues(): Array<String> = map(KeyUsage::value).toTypedArray()
