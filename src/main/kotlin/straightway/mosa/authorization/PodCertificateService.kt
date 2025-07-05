package straightway.mosa.authorization

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
        val exp = calculateExpiration(validitySeconds)
        val signingInput = buildSigningInput(gid, exp, inf)
        val signature = sign(signingInput)
        return PodCertificate(gid = gid, exp = exp, inf = inf, sig = signature)
    }

    fun isValid(cert: PodCertificate): Boolean =
        clock.instant().epochSecond < cert.exp &&
            isSignatureValid(cert)

    private fun isSignatureValid(cert: PodCertificate): Boolean {
        val key = signatureFactory.publicKey
        val signer = signatureFactory.newSigner()
        signer.initVerify(key)
        val signingInput = with(cert) { buildSigningInput(gid, exp, inf) }
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
        gid: String,
        exp: Long,
        inf: String?,
    ): ByteArray {
        val gidBytes = gid.urlDecoded()
        val expBytes = longBytes(exp)
        val infBytes = inf.urlDecoded()
        val totalLength = gidBytes.size + expBytes.size + infBytes.size

        return ByteBuffer
            .allocate(totalLength)
            .apply {
                put(gidBytes)
                put(expBytes)
                put(infBytes)
            }.array()
    }

    private fun longBytes(exp: Long): ByteArray =
        ByteBuffer
            .allocate(Long.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(exp)
            .array()

    private fun String?.urlDecoded(): ByteArray = if (this === null) ByteArray(0) else Base64.getUrlDecoder().decode(this)

    private fun sign(data: ByteArray): String {
        val signer = signatureFactory.newSigner()
        signer.update(data)
        val signatureBytes = signer.sign()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
    }
}
