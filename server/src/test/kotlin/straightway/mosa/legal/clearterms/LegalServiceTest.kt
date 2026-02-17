package straightway.mosa.legal.clearterms

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalServiceTest {
    private val service = LegalService()
    private val parser = ClearTermsJsonParser()
    private val reader = ClearTermsDocumentReader()

    @Test
    fun `provides canonical json documents for privacy baseline default and mosa`() {
        val baselineJson = service.privacyBaselineCanonicalJson()
        val defaultJson = service.privacyDefaultCanonicalJson()
        val mosaJson = service.privacyMosaCanonicalJson()

        assertTrue(baselineJson.startsWith("{"))
        assertTrue(defaultJson.startsWith("{"))
        assertTrue(mosaJson.startsWith("{"))

        val baseline = reader.readBaseline(parser.parse(baselineJson))
        val default = reader.readProfile(parser.parse(defaultJson))
        val mosa = reader.readProfile(parser.parse(mosaJson))

        assertEquals("privacy-gdpr-baseline", baseline.id)
        assertEquals("privacy-gdpr-default", default.id)
        assertEquals("mosa-privacy-gdpr-service", mosa.id)
    }

    @Test
    fun `chains parent hashes from baseline to default to mosa`() {
        val baselineJson = service.privacyBaselineCanonicalJson()
        val defaultJson = service.privacyDefaultCanonicalJson()
        val mosaJson = service.privacyMosaCanonicalJson()

        val baseline = reader.readBaseline(parser.parse(baselineJson))
        val default = reader.readProfile(parser.parse(defaultJson))
        val mosa = reader.readProfile(parser.parse(mosaJson))

        assertEquals(baseline.id, default.parent.id)
        assertEquals(baseline.version, default.parent.version)
        assertEquals(default.id, mosa.parent.id)
        assertEquals(default.version, mosa.parent.version)
        assertEquals("sha256:${sha256(baselineJson)}", default.parent.hash)
        assertEquals("sha256:${sha256(defaultJson)}", mosa.parent.hash)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
