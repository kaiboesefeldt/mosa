package straightway.mosa.content.snapshot

import java.security.SecureRandom

class SecureRandomFreezeSnapshotNonceGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
) : FreezeSnapshotNonceGenerator {
    override fun generate(): ByteArray = ByteArray(NONCE_LENGTH_BYTES).also(secureRandom::nextBytes)

    private companion object {
        const val NONCE_LENGTH_BYTES = 12
    }
}
