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
    override fun verify(jwt: DecodedJWT) =
        runCatching {
            val content = "${jwt.header}.${jwt.payload}".toByteArray(Charsets.UTF_8)
            val signatureBytes = jwt.signature.decodeBase64Bytes()
            sig {
                initVerify(keyPair.public)
                update(content)
                verify(signatureBytes)
            }
        }.getOrElse { err -> throw SignatureVerificationException(this, err) }
            .let { verified -> if (!verified) throw SignatureVerificationException(this) }

    override fun sign(contentBytes: ByteArray): ByteArray =
        sig {
            initSign(keyPair.private)
            update(contentBytes)
            sign()
        }

    private fun <R> sig(block: Signature.() -> R): R = Signature.getInstance("Ed25519").block()
}
