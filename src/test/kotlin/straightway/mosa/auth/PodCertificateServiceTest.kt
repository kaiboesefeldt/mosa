package straightway.mosa.auth

import org.junit.jupiter.api.Assertions.assertFalse
import straightway.mosa.SignatureFactory
import java.time.Clock
import java.time.Instant.ofEpochSecond
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PodCertificateServiceTest {
    val signatureFactory = TransientEd25519Factory()
    private val pointInTime = 1751710228L
    val sut = createSut(signatureFactory)

    @Test
    fun `create a certificate without inf`() {
        val base64gid = "The GID".toBase64()
        val cert = sut.approveGid(base64gid, validitySeconds = 600L)

        assertNull(cert.inf)
        assertEquals(base64gid, cert.gid)
        assertEquals(pointInTime + 600, cert.exp)
        assertTrue(sut.isValid(cert))
    }

    @Test
    fun `create a certificate with inf`() {
        val base64gid = "The GID".toBase64()
        val base64inf = "More info".toBase64()

        val cert = sut.approveGid(base64gid, validitySeconds = 600L, inf = base64inf)

        assertEquals(base64inf, cert.inf)
        assertEquals(base64gid, cert.gid)
        assertEquals(pointInTime + 600, cert.exp)
        assertTrue(sut.isValid(cert))
    }

    @Test
    fun `validation fails for an invalid certificate`() {
        val base64gid = "The GID".toBase64()

        val cert = sut.approveGid(base64gid, validitySeconds = 600L)

        // We test this using a fresh PodCertificateService, which has a new key pair
        val newSignatureFactory = TransientEd25519Factory()
        assertFalse(createSut(newSignatureFactory).isValid(cert))
    }

    @Test
    fun `validation fails for an expired certificate`() {
        val base64gid = "The GID".toBase64()

        val sut = createSut(signatureFactory)
        val cert = sut.approveGid(base64gid, validitySeconds = 600L)

        val advancedClock = createClock(pointInTime + 600)
        val sutWithAdvancedClock = createSut(signatureFactory, advancedClock)
        assertFalse(sutWithAdvancedClock.isValid(cert))
    }

    fun String.toBase64() = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(this.encodeToByteArray())

    private fun createSut(
        signatureFactory: SignatureFactory,
        clock: Clock? = null,
    ): PodCertificateService = PodCertificateService(signatureFactory, clock ?: createClock(pointInTime))

    private fun createClock(epochSeconds: Long): Clock =
        Clock.fixed(
            ofEpochSecond(epochSeconds, 0L),
            ZoneId.of("UTC"),
        )
}
