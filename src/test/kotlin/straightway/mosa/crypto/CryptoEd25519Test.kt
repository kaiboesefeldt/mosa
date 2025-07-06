package straightway.mosa.crypto

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

class CryptoEd25519Test {
    private val sut = CryptoEd25519()

    @Test
    fun `serialize - deserialize test`() {
        val originalKeyPair = sut.generateKeyPair()
        val serialized = sut.serializeKeyPair(originalKeyPair)
        val deserialized = sut.deserializeKeyPair(serialized)
        assertContentEquals(originalKeyPair.public.encoded, deserialized.public.encoded)
        assertContentEquals(originalKeyPair.private.encoded, deserialized.private.encoded)
    }
}
