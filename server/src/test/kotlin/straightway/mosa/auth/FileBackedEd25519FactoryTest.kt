package straightway.mosa.auth

import straightway.mosa.crypto.CryptoEd25519
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class FileBackedEd25519FactoryTest {
    private val crypto = CryptoEd25519()

    @Test
    fun `reuses keypair from pem file`() {
        val tempDir = Files.createTempDirectory("mosa-test-")
        val keyPath = tempDir.resolve("mosa_pod_keypair.pem")

        val firstFactory = FileBackedEd25519Factory(crypto, keyPath)

        assertTrue(Files.exists(keyPath))

        val secondFactory = FileBackedEd25519Factory(crypto, keyPath)

        assertContentEquals(firstFactory.publicKey.encoded, secondFactory.publicKey.encoded)
    }
}
