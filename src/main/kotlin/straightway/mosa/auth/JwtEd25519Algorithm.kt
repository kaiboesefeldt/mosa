package straightway.mosa.auth

import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.util.decodeBase64Bytes
import java.security.KeyPair
import java.security.Signature

class JwtEd25519Algorithm(
    val keyPair: KeyPair,
) : Algorithm("ed25519", "ed25519") {
    override fun verify(jwt: DecodedJWT) {
        val sig: Signature = Signature.getInstance("ed25519")
        sig.initVerify(keyPair.public)
        sig.update(jwt.header.decodeBase64Bytes())
        sig.update(jwt.payload.decodeBase64Bytes())
        if (!sig.verify(jwt.signature.decodeBase64Bytes())) {
            throw SignatureVerificationException(this)
        }
    }

    override fun sign(contentBytes: ByteArray): ByteArray {
        val sig: Signature = Signature.getInstance("ed25519")
        sig.initSign(keyPair.private)
        sig.update(contentBytes)
        return sig.sign()
    }
}
