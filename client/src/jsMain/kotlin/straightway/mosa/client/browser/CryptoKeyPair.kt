package straightway.mosa.client.browser

external interface CryptoKeyPair {
    val privateKey: CryptoKey
    val publicKey: CryptoKey
}