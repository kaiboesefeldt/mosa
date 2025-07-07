package straightway.mosa

import java.security.KeyPair
import java.security.PublicKey

interface Crypto {
    fun generateKeyPair(): KeyPair

    fun serializeKeyPair(keyPair: KeyPair): String

    fun deserializeKeyPair(base64: String): KeyPair

    fun deserializePublicKey(base64: String): PublicKey

    fun exportPrivateKeyPairToPem(keyPair: KeyPair): String

    fun importKeyPairFromPem(pem: String): KeyPair
}
