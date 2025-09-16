package straightway.mosa.client

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array

class CryptoExport(private val subtleCrypto: SubtleCrypto) {

    suspend fun pubRawOf(keyPair: CryptoKeyPair): Uint8Array {
        val pubBuf: ArrayBuffer = subtleCrypto.exportKey("raw", keyPair.publicKey)
        return Uint8Array(pubBuf)
    }

    suspend fun privPkcs8Of(keyPair: CryptoKeyPair): Uint8Array {
        val privateBuf: ArrayBuffer = subtleCrypto.exportKey("pkcs8", keyPair.privateKey)
        return Uint8Array(privateBuf)
    }
}