package straightway.mosa.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.mockk.MockKException
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JwtEd25519AlgorithmTest {
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val algorithm = JwtEd25519Algorithm(keyPair)

    @Test
    fun `sign returns signature verifiable with the key pair`() {
        val content = "header.payload".encodeToByteArray()

        val signature = algorithm.sign(content)

        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(keyPair.public)
        verifier.update(content)
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun `verify succeeds for tokens signed with the key pair`() {
        val token = JWT.create().withClaim("scope", "test").sign(algorithm)
        val decoded = JWT.decode(token)

        assertDoesNotThrow { algorithm.verify(decoded) }
    }

    @Test
    fun `verify rejects tokens with a tampered signature`() {
        val token = JWT.create().sign(algorithm)
        val parts = token.split(".")
        val tamperedSignature = mutateBase64Url(parts[2])
        val tamperedToken = listOf(parts[0], parts[1], tamperedSignature).joinToString(".")
        val decoded = JWT.decode(tamperedToken)

        assertThrows<SignatureVerificationException> { algorithm.verify(decoded) }
    }

    @Test
    fun `verify rejects if signature calculation raises an exception`() {
        val decoded = mockk<DecodedJWT>()
        val ex = assertThrows<SignatureVerificationException> { algorithm.verify(decoded) }
        assertIs<MockKException>(ex.cause)
    }

    private fun mutateBase64Url(signature: String): String {
        val replacement = if (signature.first() == 'A') 'B' else 'A'
        return replacement + signature.drop(1)
    }
}
