package straightway.mosa

import java.security.KeyPair

interface Crypto {
    fun generateKeyPair(): KeyPair

    fun serializeKeyPair(keyPair: KeyPair): String

    fun deserializeKeyPair(base64: String): KeyPair

    fun exportPrivateKeyToPem(keyPair: KeyPair): String

    fun importPrivateKeyFromPem(pem: String): KeyPair
}
