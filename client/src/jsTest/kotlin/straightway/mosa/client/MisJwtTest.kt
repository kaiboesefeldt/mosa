package straightway.mosa.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import straightway.mosa.client.Base64.toBase64Url
import straightway.mosa.auth.PodCertificate

class MisJwtTest {
    @Test
    fun `buildMisJwt produces correct header and payload`() = runTest {
        // Arrange: start DI and generate identity
        ClientDI.start()
        val identity = KoinHolder.identityGenerator.generate()

        val now = (kotlin.js.Date.now().toLong() / 1000L)
        val cert = PodCertificate(
            gid = identity.gid,
            exp = now + 600,
            sig = "deadbeefcafebabe",
            inf = "additional-info",
        )
        val lifetime = 120L

        // Act
        val jwt = buildMisJwt(identity, cert, lifetimeSeconds = lifetime)

        // Assert: structure
        val parts = jwt.split('.')
        assertEquals(3, parts.size, "JWT should have three segments")
        val (hB64, pB64, sigB64) = parts
        assertTrue(hB64.isNotBlank(), "Header part present")
        assertTrue(pB64.isNotBlank(), "Payload part present")
        assertTrue(sigB64.isNotBlank(), "Signature part present")

        fun decodeB64UrlToString(b64: String): String {
            val bytes = Base64.fromBase64Url(b64)
            val decoder = js("new TextDecoder('utf-8')")
            return decoder.decode(bytes) as String
        }

        // Decode header/payload JSON
        val headerJson = decodeB64UrlToString(hB64)
        val payloadJson = decodeB64UrlToString(pB64)

        // Parse using the same DTOs as in implementation
        val header = Json.decodeFromString<JwtHeader>(headerJson)
        val payload = Json.decodeFromString<JwtPayload>(payloadJson)

        // Header checks
        assertEquals("ed25519", header.alg)
        assertEquals("JWT", header.typ)

        // Payload checks
        val expectedPub = identity.keyPair.pubRaw().toBase64Url()
        assertEquals(expectedPub, payload.pub)
        assertEquals("1", payload.mvs)

        // exp close to now + lifetime (tolerance 5s)
        val expectedExp = (now + lifetime).toInt()
        val diff = kotlin.math.abs(payload.exp - expectedExp)
        assertTrue(diff <= 5, "exp should be within 5s of expected; diff=$diff")

        // cert embedded correctly
        assertEquals(cert.gid, payload.cert.gid)
        assertEquals(cert.exp, payload.cert.exp)
        assertEquals(cert.sig, payload.cert.sig)
        assertEquals(cert.inf, payload.cert.inf)
    }
}
