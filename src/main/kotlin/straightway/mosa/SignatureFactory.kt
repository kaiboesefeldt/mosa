package straightway.mosa

import java.security.PublicKey
import java.security.Signature

interface SignatureFactory {
    val publicKey: PublicKey

    fun newSigner(): Signature
}
