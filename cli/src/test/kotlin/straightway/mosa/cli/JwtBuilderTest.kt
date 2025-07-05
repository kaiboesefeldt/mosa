package straightway.mosa.cli

import com.auth0.jwt.JWT
import org.junit.jupiter.api.Test
import straightway.mosa.authorization.PodCertificateService
import straightway.mosa.authorization.TransientEd25519Factory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant.ofEpochSecond
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

class JwtBuilderTest {
    private val clock = createClock(1751710228L)
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val gid = keyPair.public.encoded.digest().toBase64()
    private val podCertService = PodCertificateService(TransientEd25519Factory(), clock)
    private val certValidity = 7.days
    private val podCert = podCertService.approveGid(
        gid,
        validitySeconds = certValidity.inWholeSeconds
    )

    @Test
    fun `buildMisJwt creates a proper JWT`() {
        val sut = JwtBuilder(
            clock,
            24.hours
        )

        val jwt = sut.buildMisJwt(keyPair, podCert)

        val decoded = JWT.decode(jwt)
        decoded.apply {
            assertEquals("ed25519", algorithm)
            assertEquals("JWT", type)
            assertEquals(clock.instant() + 1.days.toJavaDuration(), expiresAtAsInstant)
            assertEquals(claims["pub"]?.asString(), keyPair.public.encoded.toBase64())
            assertEquals(claims["mvs"]?.asString(), "1")

            val cert = claims["cert"]?.asMap()!!
            assertEquals(podCert.gid, cert["gid"])
            assertEquals(gid, cert["gid"])
            assertEquals((clock.instant() + certValidity.toJavaDuration()).epochSecond.toInt(), cert["exp"])
            assertEquals(podCert.exp.toInt(), cert["exp"])
            assertEquals(podCert.sig, cert["sig"])
        }
    }

    private fun createClock(epochSeconds: Long): Clock =
        Clock.fixed(
            ofEpochSecond(epochSeconds, 0L),
            ZoneId.of("UTC"),
        )

    private fun ByteArray.toBase64() =
        Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(this)

    private fun ByteArray.digest() =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
}