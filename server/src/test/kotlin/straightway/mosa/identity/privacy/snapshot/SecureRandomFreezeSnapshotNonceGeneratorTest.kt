package straightway.mosa.identity.privacy.snapshot

import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SecureRandomFreezeSnapshotNonceGeneratorTest {
    @Test
    fun `generates twelve random bytes`() {
        val sut = SecureRandomFreezeSnapshotNonceGenerator(DeterministicSecureRandom())

        val nonce = sut.generate()

        assertEquals(12, nonce.size)
        assertContentEquals(ByteArray(12) { (it + 1).toByte() }, nonce)
    }

    private class DeterministicSecureRandom : SecureRandom() {
        override fun nextBytes(bytes: ByteArray) {
            bytes.indices.forEach { bytes[it] = (it + 1).toByte() }
        }
    }
}
