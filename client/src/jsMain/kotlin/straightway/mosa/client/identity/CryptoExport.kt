package straightway.mosa.client.identity

import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.SubtleCrypto

class CryptoExport(private val subtleCrypto: SubtleCrypto) {

    suspend fun pubRawOf(keyPair: CryptoKeyPair): ByteArray =
        subtleCrypto.exportKey("raw", keyPair.publicKey)

    suspend fun privPkcs8Of(keyPair: CryptoKeyPair): ByteArray =
        subtleCrypto.exportKey("pkcs8", keyPair.privateKey)
}
