package straightway.mosa.client.identity

import kotlinx.coroutines.test.runTest
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.aux.TimeService
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.AppModule
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import straightway.mosa.client.util.Base64
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.client.browser.KeyAlgorithm
import straightway.mosa.client.browser.KeyUsage
import straightway.mosa.client.browser.CryptoKeyPair
import kotlin.js.json
import kotlin.js.unsafeCast
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MisJwtServiceTest : KoinComponent {
    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @BeforeTest
    fun startDI() {
        AppModule.start()
    }

    private val sut by lazy {
        MisJwtService(
            object : TimeService {
                override val now = NOW
            },
            get<CryptoExport>(),
            get<SubtleCrypto>()
        )
    }

    @Test
    fun `buildMisJwt produces correct header and payload with inf`() = runTest {
        // Arrange: start DI and generate identity
        AppModule.start()
        val identity = this@MisJwtServiceTest.get<IdentityGenerator>().generate()

        val cert = PodCertificate(
            gid = identity.gid,
            exp = NOW + 600,
            sig = "deadbeefcafebabe",
            inf = "additional-info",
        )

        // Act
        val jwt = sut.buildMisJwt(identity, cert, lifetimeSeconds = LIFETIME_SECONDS)

        // Assert
        assertJwtValuesMatch(jwt, identity, cert)
    }

    @Test
    fun `buildMisJwt produces correct header and payload without inf`() = runTest {
        // Arrange: start DI and generate identity
        AppModule.start()
        val identity = this@MisJwtServiceTest.get<IdentityGenerator>().generate()

        val cert = PodCertificate(
            gid = identity.gid,
            exp = NOW + 600,
            sig = "deadbeefcafebabe",
            inf = null,
        )

        // Act
        val jwt = sut.buildMisJwt(identity, cert, lifetimeSeconds = LIFETIME_SECONDS)

        // Assert
        assertJwtValuesMatch(jwt, identity, cert)
    }

    @Test
    fun `buildMisJwt works with protected identity`() = runTest {
        AppModule.start()
        val generator = this@MisJwtServiceTest.get<IdentityGenerator>()
        val protector = this@MisJwtServiceTest.get<IdentityProtector>()
        val identity = generator.generate()
        val expectedPub = this@MisJwtServiceTest.get<CryptoExport>().pubRawOf(identity.keyPair).toBase64Url()
        val cert = PodCertificate(
            gid = identity.gid,
            exp = NOW + 600,
            sig = "deadbeefcafebabe",
            inf = "info",
        )

        val protectedIdentity = protector.protect(identity, "password")

        val jwt = sut.buildMisJwt(protectedIdentity, cert, lifetimeSeconds = LIFETIME_SECONDS)

        val parts = jwt.split('.')
        val payloadJson = decodeB64UrlToString(parts[1])
        val payload = JSON.parse<dynamic>(payloadJson)
        assertEquals(expectedPub, payload.pub as String)
    }

    @Test
    fun `buildMisJwt succeeds when public key is non exportable`() = runTest {
        AppModule.start()
        val generator = this@MisJwtServiceTest.get<IdentityGenerator>()
        val protector = this@MisJwtServiceTest.get<IdentityProtector>()
        val cryptoExport = this@MisJwtServiceTest.get<CryptoExport>()
        val subtle = this@MisJwtServiceTest.get<SubtleCrypto>()
        val identity = generator.generate()
        val publicBytes = cryptoExport.pubRawOf(identity.keyPair)
        val nonExportablePublicKey =
            subtle.importKey("raw", publicBytes, KeyAlgorithm.Ed25519, false, arrayOf(KeyUsage.Verify))
        val identityWithConstrainedPublic =
            identity.copy(
                keyPair = json(
                    "publicKey" to nonExportablePublicKey,
                    "privateKey" to identity.keyPair.privateKey,
                ).unsafeCast<CryptoKeyPair>(),
                publicKeyBase64 = publicBytes.toBase64Url(),
            )
        val protectedIdentity = protector.protect(identityWithConstrainedPublic, "password")
        val cert = PodCertificate(
            gid = identity.gid,
            exp = NOW + 600,
            sig = "deadbeefcafebabe",
            inf = "info",
        )

        val jwt = sut.buildMisJwt(protectedIdentity, cert, lifetimeSeconds = LIFETIME_SECONDS)

        val parts = jwt.split('.')
        assertEquals(3, parts.size)
        val payload = JSON.parse<dynamic>(decodeB64UrlToString(parts[1]))
        assertEquals(publicBytes.toBase64Url(), payload.pub as String)
    }

    private suspend fun assertJwtValuesMatch(
        jwt: String,
        identity: Identity,
        cert: PodCertificate
    ) {
        // Assert: structure
        val parts = jwt.split('.')
        assertEquals(3, parts.size, "JWT should have three segments")
        val (hB64, pB64, sigB64) = parts
        assertTrue(hB64.isNotBlank(), "Header part present")
        assertTrue(pB64.isNotBlank(), "Payload part present")
        assertTrue(sigB64.isNotBlank(), "Signature part present")

        // Decode header/payload JSON
        val headerJson = decodeB64UrlToString(hB64)
        val payloadJson = decodeB64UrlToString(pB64)

        // Parse natively (dynamic)
        val header = JSON.parse<dynamic>(headerJson)
        val payload = JSON.parse<dynamic>(payloadJson)

        // Header checks
        assertEquals("ed25519", header.alg as String)
        assertEquals("JWT", header.typ as String)
        val undef = js("undefined")
        assertTrue(header.cty === undef)
        assertTrue(header.kid === undef)
        // Note: do not rely on Object.keys in typed context; basic checks above suffice

        val expectedPub = get<CryptoExport>().pubRawOf(identity.keyPair).toBase64Url()
        assertEquals(expectedPub, payload.pub as String)
        assertEquals("1", payload.mvs as String)
        assertEquals(NOW + LIFETIME_SECONDS, (payload.exp as Number).toLong())

        val certObj = payload.cert
        assertEquals(cert.gid, certObj.gid as String)
        assertEquals(cert.exp, (certObj.exp as Number).toLong())
        assertEquals(cert.sig, certObj.sig as String)
        assertEquals(cert.inf, certObj.inf as String?)
    }

    private companion object {
        const val LIFETIME_SECONDS = 120L
        const val NOW = 1000L
    }

    private fun decodeB64UrlToString(b64: String): String {
        val bytes = Base64.fromBase64Url(b64)
        val decoder = js("new TextDecoder('utf-8')")
        return decoder.decode(bytes) as String
    }
}
