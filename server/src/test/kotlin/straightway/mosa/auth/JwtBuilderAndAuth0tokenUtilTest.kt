package straightway.mosa.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.mockk.mockk
import org.junit.jupiter.api.Test
import straightway.mosa.HasherFactory
import straightway.mosa.crypto.CryptoEd25519
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.time.Clock.fixed
import java.time.Instant.ofEpochSecond
import java.time.ZoneId.of
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

class JwtBuilderAndAuth0tokenUtilTest {
    private val clock =
        fixed(
            ofEpochSecond(1751710228L, 0L),
            of("UTC"),
        )
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val gid =
        keyPair.public.encoded
            .takeLast(32)
            .toByteArray()
            .digest()
            .toBase64()
    private val podSignatureFactory = TransientEd25519Factory()
    private val podCertService = PodCertificateService(podSignatureFactory, clock)
    private val certValidity = 7.days
    private val podCert =
        podCertService.approveGid(
            gid,
            validitySeconds = certValidity.inWholeSeconds,
        )
    val crypto = CryptoEd25519()
    val hasherFactory: HasherFactory = { MessageDigest.getInstance("SHA256") }
    val tokenUtil = Auth0TokenUtil(crypto, podCertService, clock, hasherFactory)
    val jwtBuilder = JwtBuilder(clock, 24.hours)

    @Test
    fun `buildMisJwt creates a proper JWT`() {
        val jwt = jwtBuilder.buildMisJwt(keyPair, podCert)

        val decoded = tokenUtil.decode(jwt)
        decoded.apply {
            assertEquals("ed25519", algorithm)
            assertEquals("JWT", type)
            assertEquals(
                clock.instant() + 1.days.toJavaDuration(),
                expiresAtAsInstant,
            )
            assertEquals(
                keyPair.public.encoded
                    .takeLast(32)
                    .toByteArray()
                    .toBase64(),
                claims["pub"]?.asString(),
            )
            assertEquals(claims["mvs"]?.asString(), "1")

            val cert = claims["cert"]?.asMap()!!
            assertEquals(podCert.gid, cert["gid"])
            assertEquals(gid, cert["gid"])
            assertEquals(
                (clock.instant() + certValidity.toJavaDuration()).epochSecond.toInt(),
                cert["exp"],
            )
            assertEquals(podCert.exp.toInt(), cert["exp"])
            assertEquals(podCert.sig, cert["sig"])
        }
    }

    @Test
    fun `buildMisJwt result is correctly verified by Auth0TokenUtil`() {
        val jwt = jwtBuilder.buildMisJwt(keyPair, podCert)

        val decoded = tokenUtil.decode(jwt)
        assertTrue(tokenUtil.isValidMisJwt(decoded))
    }

    @Test
    fun `invalid JWT signature makes JWT invalid`() {
        val otherKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val jwt = createTestJwt(otherKeyPair)
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `corrupted signature makes JWT invalid`() {
        val corruptedJwt = mockk<DecodedJWT>(relaxed = true)
        assertFalse(tokenUtil.isValidMisJwt(corruptedJwt))
    }

    @Test
    fun `additional key id header makes JWT invalid`() {
        val jwt = createTestJwt(headerOverrides = mapOf("kid" to "Key ID"))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `additional content type header makes JWT invalid`() {
        val jwt = createTestJwt(headerOverrides = mapOf("cty" to "ContentType"))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `different type header makes JWT invalid`() {
        val otherKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val jwt = createTestJwt(headerOverrides = mapOf("typ" to otherKeyPair.public.toBase64()))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `different algorithm header makes JWT invalid`() {
        class TestAlgo(
            val wrapped: JwtEd25519Algorithm,
        ) : Algorithm("other", "other") {
            override fun verify(jwt: DecodedJWT) = wrapped.verify(jwt)

            override fun sign(contentBytes: ByteArray): ByteArray = wrapped.sign(contentBytes)
        }

        val jwt = createTestJwt { TestAlgo(JwtEd25519Algorithm(keyPair)) }
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `missing pod cert makes JWT invalid`() {
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to null))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `missing gid in pod cert makes JWT invalid`() {
        val podCertWithoutGid = podCert.toMap().filterKeys { it != "gid" }
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithoutGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `gid in pod cert not matching pub makes JWT invalid`() {
        val invalidPodCert =
            podCertService.approveGid(
                "otherGid",
                validitySeconds = certValidity.inWholeSeconds,
            )
        val podCertWithDifferentGid = invalidPodCert.toMap()
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithDifferentGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `null gid in pod cert makes JWT invalid`() {
        val podCertWithoutGid = podCert.toMap() + mapOf("gid" to null)
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithoutGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `missing exp in pod cert makes JWT invalid`() {
        val podCertWithoutGid = podCert.toMap().filterKeys { it != "exp" }
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithoutGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `null exp in pod cert makes JWT invalid`() {
        val podCertWithoutExp = podCert.toMap() + mapOf("exp" to null)
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithoutExp))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `exp as string in pod cert makes JWT invalid`() {
        val podCertWithoutGid = podCert.toMap() + mapOf("exp" to "soon")
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithoutGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `exp as floating point value in pod cert makes JWT invalid`() {
        val podCertWithoutGid = podCert.toMap() + mapOf("exp" to 3.1415)
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithoutGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `missing sig in pod cert makes JWT invalid`() {
        val podCertWithoutGid = podCert.toMap().filterKeys { it != "sig" }
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithoutGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `null sig in pod cert makes JWT invalid`() {
        val podCertWithoutSig = podCert.toMap() + mapOf("sig" to null)
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithoutSig))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `sig as int in pod cert makes JWT invalid`() {
        val podCertWithModifiedGid = podCert.toMap() + mapOf("sig" to 83)
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithModifiedGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `invalid sig in pod cert makes JWT invalid`() {
        val podCertWithModifiedGid = podCert.toMap() + mapOf("sig" to 83)
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithModifiedGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `not matching sig in pod cert makes JWT invalid`() {
        // Adding additional info to the cert makes the signature invalid
        val podCertWithModifiedGid = podCert.toMap() + mapOf("inf" to "additional info")
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithModifiedGid))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `null inf in pod cert is the same as missing inf`() {
        val podCertWithModifiedGid = podCert.toMap() + mapOf("inf" to null)
        val jwt = createTestJwt(payloadOverrides = mapOf("cert" to podCertWithModifiedGid))
        assertTrue(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `expired pod cert makes JWT invalid`() {
        val podCert = podCertService.approveGid(gid, -1000)
        val jwt =
            createTestJwt(
                payloadOverrides = mapOf("cert" to podCert.toMap()),
            )
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `missing pub key makes JWT invalid`() {
        val jwt =
            createTestJwt(
                payloadOverrides = mapOf("pub" to null),
            )
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `wrong pub key makes JWT invalid`() {
        val jwt = createTestJwt(payloadOverrides = mapOf("pub" to "wring pub key"))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `different pub key makes JWT invalid`() {
        val jwt = createTestJwt(payloadOverrides = mapOf("pub" to "wring pub key"))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `missing exp makes JWT invalid`() {
        val jwt = createTestJwt(payloadOverrides = mapOf("exp" to null))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `expired exp makes JWT invalid`() {
        val jwt =
            createTestJwt(
                payloadOverrides = mapOf("exp" to clock.instant().epochSecond - 1000),
            )
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `missing mvs makes JWT invalid`() {
        val jwt = createTestJwt(payloadOverrides = mapOf("mvs" to null))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    @Test
    fun `additional payload claim makes JWT invalid`() {
        val jwt = createTestJwt(payloadOverrides = mapOf("additional" to "additional"))
        assertFalse(tokenUtil.isValidMisJwt(jwt))
    }

    private fun createTestJwt(
        signKeyPair: KeyPair? = null,
        headerOverrides: Map<String, String> = emptyMap(),
        payloadOverrides: Map<String, Any?> = emptyMap(),
        algorithm: (KeyPair) -> Algorithm = { JwtEd25519Algorithm(it) },
    ): DecodedJWT {
        val headerClaims =
            mapOf(
                "alg" to "ed25519",
                "typ" to "JWT",
            ) + headerOverrides
        val payloadClaims =
            (
                mapOf(
                    "pub" to keyPair.public.toBase64(),
                    "cert" to podCert.toMap(),
                    "exp" to (clock.instant() + 24.hours.toJavaDuration()).epochSecond,
                    "mvs" to "1",
                ) + payloadOverrides
            ).filterValues { it !== null }
        return tokenUtil.decode(
            JWT
                .create()
                .withHeader(headerClaims)
                .withPayload(payloadClaims)
                .sign(algorithm(signKeyPair ?: keyPair)),
        )
    }

    private fun ByteArray.toBase64() =
        Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(this)

    private fun ByteArray.digest() =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)

    private fun PublicKey.toBase64(): String =
        Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(this.encoded.takeLast(32).toByteArray())

    private fun PodCertificate.toMap(): Map<String, Any> =
        mapOf(
            "gid" to gid,
            "exp" to exp,
            "sig" to sig,
        ) + if (inf === null) emptyMap() else mapOf<String, Any>("inf" to inf)
}
