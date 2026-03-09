package straightway.mosa.content

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class FreezeSnapshotCryptoService(
    private val archiveKeyStore: FreezeSnapshotArchiveKeyStore,
    private val format: FreezeSnapshotFormat,
) {
    fun encrypt(
        header: FreezeSnapshotHeader,
        payload: ByteArray,
    ): ByteArray = runCipher(header, payload, Cipher.ENCRYPT_MODE, "encryption")

    fun decrypt(
        header: FreezeSnapshotHeader,
        encryptedPayload: ByteArray,
    ): ByteArray = runCipher(header, encryptedPayload, Cipher.DECRYPT_MODE, "decryption")

    private fun runCipher(
        header: FreezeSnapshotHeader,
        input: ByteArray,
        mode: Int,
        operation: String,
    ): ByteArray =
        try {
            val key = requireKey(header.keyId)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(mode, SecretKeySpec(key.secret, ALGORITHM), GCMParameterSpec(TAG_LENGTH_BITS, decodeNonce(header.nonce)))
            cipher.updateAAD(format.serializeHeader(header).encodeToByteArray())
            cipher.doFinal(input)
        } catch (exception: InvalidFreezeSnapshotException) {
            throw exception
        } catch (exception: GeneralSecurityException) {
            throw InvalidFreezeSnapshotException("Snapshot payload $operation failed", exception)
        }

    private fun requireKey(keyId: String): FreezeSnapshotArchiveKey =
        archiveKeyStore.findByKeyId(keyId) ?: throw InvalidFreezeSnapshotException("Unknown archive key: $keyId")

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeNonce(nonce: String): ByteArray {
        val decoded =
            try {
                Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(nonce)
            } catch (exception: IllegalArgumentException) {
                throw InvalidFreezeSnapshotException("Snapshot nonce is invalid", exception)
            }
        if (decoded.size != NONCE_LENGTH_BYTES) {
            throw InvalidFreezeSnapshotException("Snapshot nonce length is invalid")
        }
        return decoded
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val NONCE_LENGTH_BYTES = 12
    }
}
