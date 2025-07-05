package straightway.mosa.authorization

import straightway.mosa.SignatureFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature

class TransientEd25519Factory : SignatureFactory {
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    override val publicKey: PublicKey get() = keyPair.public

    override fun newSigner(): Signature =
        Signature.getInstance("Ed25519").apply {
            initSign(keyPair.private)
        }
}
