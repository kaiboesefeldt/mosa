package straightway.mosa.content.snapshot

import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class FreezeSnapshotCryptoServiceTest {
    private val format = FreezeSnapshotFormat()

    @Test
    fun `encrypt then decrypt returns original payload`() {
        val header = sampleHeader()
        val payload = "snapshot-payload".encodeToByteArray()
        val service = FreezeSnapshotCryptoService(FixedArchiveKeyStore(sampleKey(header.keyId)), format)

        val encrypted = service.encrypt(header, payload)
        val decrypted = service.decrypt(header, encrypted)

        assertContentEquals(payload, decrypted)
    }

    @Test
    fun `modified ciphertext is rejected`() {
        val header = sampleHeader()
        val payload = "snapshot-payload".encodeToByteArray()
        val service = FreezeSnapshotCryptoService(FixedArchiveKeyStore(sampleKey(header.keyId)), format)
        val encrypted = service.encrypt(header, payload).copyOf()
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        assertFailsWith<InvalidFreezeSnapshotException> {
            service.decrypt(header, encrypted)
        }
    }

    @Test
    fun `modified header associated data is rejected`() {
        val header = sampleHeader()
        val payload = "snapshot-payload".encodeToByteArray()
        val service = FreezeSnapshotCryptoService(FixedArchiveKeyStore(sampleKey(header.keyId)), format)
        val encrypted = service.encrypt(header, payload)
        val tamperedHeader = header.copy(createdAt = header.createdAt.plusSeconds(1))

        assertFailsWith<InvalidFreezeSnapshotException> {
            service.decrypt(tamperedHeader, encrypted)
        }
    }

    @Test
    fun `wrong key is rejected`() {
        val header = sampleHeader()
        val payload = "snapshot-payload".encodeToByteArray()
        val encryptingService = FreezeSnapshotCryptoService(FixedArchiveKeyStore(sampleKey(header.keyId)), format)
        val decryptingService = FreezeSnapshotCryptoService(FixedArchiveKeyStore(otherKey(header.keyId)), format)
        val encrypted = encryptingService.encrypt(header, payload)

        assertFailsWith<InvalidFreezeSnapshotException> {
            decryptingService.decrypt(header, encrypted)
        }
    }

    @Test
    fun `unknown key id is rejected`() {
        val header = sampleHeader()
        val payload = "snapshot-payload".encodeToByteArray()
        val encryptingService = FreezeSnapshotCryptoService(FixedArchiveKeyStore(sampleKey(header.keyId)), format)
        val decryptingService = FreezeSnapshotCryptoService(FixedArchiveKeyStore(), format)
        val encrypted = encryptingService.encrypt(header, payload)

        assertFailsWith<InvalidFreezeSnapshotException> {
            decryptingService.decrypt(header, encrypted)
        }
    }

    @Test
    fun `invalid nonce encoding is rejected`() {
        val header = sampleHeader().copy(nonce = "%%%")
        val payload = "snapshot-payload".encodeToByteArray()
        val service = FreezeSnapshotCryptoService(FixedArchiveKeyStore(sampleKey(header.keyId)), format)

        assertFailsWith<InvalidFreezeSnapshotException> {
            service.encrypt(header, payload)
        }
    }

    @Test
    fun `invalid nonce length is rejected`() {
        val header = sampleHeader().copy(nonce = shortNonce())
        val payload = "snapshot-payload".encodeToByteArray()
        val service = FreezeSnapshotCryptoService(FixedArchiveKeyStore(sampleKey(header.keyId)), format)

        assertFailsWith<InvalidFreezeSnapshotException> {
            service.encrypt(header, payload)
        }
    }

    @Test
    fun `invalid aes key is rejected`() {
        val header = sampleHeader()
        val payload = "snapshot-payload".encodeToByteArray()
        val invalidKey = FreezeSnapshotArchiveKey(header.keyId, ByteArray(15) { it.toByte() })
        val service = FreezeSnapshotCryptoService(FixedArchiveKeyStore(invalidKey), format)

        assertFailsWith<InvalidFreezeSnapshotException> {
            service.encrypt(header, payload)
        }
    }

    private fun sampleHeader() =
        FreezeSnapshotHeader(
            formatVersion = FreezeSnapshotFormat.CURRENT_FORMAT_VERSION,
            podId = "pod-1",
            keyId = "archive-2026",
            createdAt = java.time.Instant.ofEpochSecond(1_700_000_000),
            nonce = nonce(),
        )

    @OptIn(ExperimentalEncodingApi::class)
    private fun nonce(): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(12) { it.toByte() })

    @OptIn(ExperimentalEncodingApi::class)
    private fun shortNonce(): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(8) { it.toByte() })

    private fun sampleKey(keyId: String) = FreezeSnapshotArchiveKey(keyId, ByteArray(16) { (it + 1).toByte() })

    private fun otherKey(keyId: String) = FreezeSnapshotArchiveKey(keyId, ByteArray(16) { (it + 31).toByte() })

    private class FixedArchiveKeyStore(
        vararg keys: FreezeSnapshotArchiveKey,
    ) : FreezeSnapshotArchiveKeyStore {
        private val keysById = keys.associateBy { it.keyId }

        override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = keysById[keyId]
    }
}
