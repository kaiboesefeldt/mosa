package straightway.mosa.client.browser

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

class SubtleCrypto(private val wrapped: dynamic) {
    suspend fun sign(algo: String, privateKey: CryptoKey, signingBytes: Uint8Array): ArrayBuffer =
        wrapped
            .sign(algo, privateKey, signingBytes)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()

    suspend fun exportKey(format: String, key: CryptoKey): ArrayBuffer =
        wrapped
            .exportKey(format, key)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()

    suspend fun generateKey(algo: String, exportable: Boolean, usages: Array<String>): CryptoKeyPair =
        wrapped
            .generateKey(algo, exportable, usages)
            .unsafeCast<Promise<CryptoKeyPair>>()
            .await()

    suspend fun digest(algo: String, data: Uint8Array): ArrayBuffer =
        wrapped
            .digest(algo, data)
            .unsafeCast<Promise<ArrayBuffer>>()
            .await()
}