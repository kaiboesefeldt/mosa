package straightway.mosa.auth

import straightway.mosa.SignatureFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.util.Base64

class PodCertificateService(
    private val signatureFactory: SignatureFactory,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun approveGid(
        gid: String,
        validitySeconds: Long,
        inf: String? = null,
    ): PodCertificate {
        val gidBytes = gid.decodeBase64UrlOrNull() ?: throw InvalidPodCertificateRequest("gid")
        val infBytes =
            when (inf) {
                null -> ByteArray(0)
                else -> inf.decodeBase64UrlOrNull() ?: throw InvalidPodCertificateRequest("inf")
            }
        val exp = calculateExpiration(validitySeconds)
        val signingInput = buildSigningInput(gidBytes, exp, infBytes)
        val signature = sign(signingInput)
        return PodCertificate(gid = gid, exp = exp, inf = inf, sig = signature)
    }

    fun isValid(cert: PodCertificate): Boolean = clock.instant().epochSecond < cert.exp && isSignatureValid(cert)

    private fun isSignatureValid(cert: PodCertificate): Boolean {
        val key = signatureFactory.publicKey
        val signer = signatureFactory.newSigner()
        signer.initVerify(key)
        val gidBytes = cert.gid.decodeBase64UrlOrNull() ?: return false
        val infBytes =
            when (val value = cert.inf) {
                null -> ByteArray(0)
                else -> value.decodeBase64UrlOrNull() ?: return false
            }
        val signingInput = buildSigningInput(gidBytes, cert.exp, infBytes)
        val sigBytes =
            kotlin.io.encoding.Base64.UrlSafe
                .withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
                .decode(cert.sig)
        signer.update(signingInput)
        return signer.verify(sigBytes)
    }

    private fun calculateExpiration(validitySeconds: Long): Long {
        val now = clock.instant().epochSecond
        return now + validitySeconds
    }

    private fun buildSigningInput(
        gid: ByteArray,
        exp: Long,
        inf: ByteArray,
    ): ByteArray {
        val expBytes = longBytes(exp)
        val totalLength = gid.size + expBytes.size + inf.size

        return ByteBuffer
            .allocate(totalLength)
            .apply {
                put(gid)
                put(expBytes)
                put(inf)
            }.array()
    }

    private fun longBytes(exp: Long): ByteArray =
        ByteBuffer
            .allocate(Long.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(exp)
            .array()

    private fun String.decodeBase64UrlOrNull(): ByteArray? = runCatching { Base64.getUrlDecoder().decode(this) }.getOrNull()

    private fun sign(data: ByteArray): String {
        val signer = signatureFactory.newSigner()
        signer.update(data)
        val signatureBytes = signer.sign()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
    }
}

class InvalidPodCertificateRequest(
    field: String,
) : IllegalArgumentException("Invalid base64url encoding for $field")
