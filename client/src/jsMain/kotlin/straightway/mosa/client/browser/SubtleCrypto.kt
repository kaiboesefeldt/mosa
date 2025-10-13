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
        algorithm: dynamic,
        exportable: Boolean,
        usages: Array<String>,
    ): CryptoKey =
        wrapped
            .importKey(format, keyData, algorithm, exportable, usages)
            .unsafeCast<Promise<CryptoKey>>()
            .await()

    suspend fun generateKey(algo: String, exportable: Boolean, usages: Array<String>): CryptoKeyPair =
        wrapped
            .generateKey(algo, exportable, usages)
            .unsafeCast<Promise<CryptoKeyPair>>()
            .await()

    suspend fun digest(algo: String, data: ByteArray): ByteArray =
        wrapped
            .digest(algo, data)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()
            .toByteArray()
}
