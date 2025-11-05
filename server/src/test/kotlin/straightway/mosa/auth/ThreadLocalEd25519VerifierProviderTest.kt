package straightway.mosa.auth

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThreadLocalEd25519VerifierProviderTest {
    private val provider = ThreadLocalEd25519VerifierProvider()

    @Test
    fun `returns same instance on same thread`() {
        val first = provider.get()
        val second = provider.get()
        assertSame(first, second)
    }

    @Test
    fun `returns different instances on different threads`() {
        val first = provider.get()
        val otherThreadVerifier = CompletableFuture<Signature>()
        val thread =
            Thread {
                otherThreadVerifier.complete(provider.get())
            }
        thread.start()
        thread.join()
        assertNotSame(first, otherThreadVerifier.get())
    }

    @Test
    fun `reused verifier validates sequential signatures`() {
        val keyPair = generateKeyPair()
        val messageOne = "one"
        val messageTwo = "two"
        val signatureOne = sign(keyPair, messageOne)
        val signatureTwo = sign(keyPair, messageTwo)
        val verifier = provider.get()
        verifier.initVerify(keyPair.public)
        verifier.update(messageOne.toByteArray(Charsets.UTF_8))
        assertTrue(verifier.verify(signatureOne))
        verifier.initVerify(keyPair.public)
        verifier.update(messageTwo.toByteArray(Charsets.UTF_8))
        assertTrue(verifier.verify(signatureTwo))
    }

    private fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private fun sign(
        keyPair: KeyPair,
        message: String,
    ): ByteArray =
        Signature
            .getInstance("Ed25519")
            .apply {
                initSign(keyPair.private)
                update(message.toByteArray(Charsets.UTF_8))
            }.sign()
}
