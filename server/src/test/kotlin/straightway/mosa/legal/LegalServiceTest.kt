package straightway.mosa.legal

import io.mockk.every
import io.mockk.mockk
import straightway.mosa.legal.clearterms.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.ClearTermsProfileChainCompletenessChecker
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegalServiceTest {
    private val parser = ClearTermsJsonParser()
    private val reader = ClearTermsDocumentReader()

    @Test
    fun `provides canonical json documents for privacy baseline default mosa and service`() {
        val service =
            LegalService(
                operatorProfilePath = writeOperatorProfile(),
                completenessChecker = completenessChecker(complete = true),
            )

        val baselineJson = service.privacyBaselineCanonicalJson()
        val defaultJson = service.privacyDefaultCanonicalJson()
        val mosaJson = service.privacyMosaCanonicalJson()
        val serviceJson = service.privacyServiceCanonicalJson()

        assertTrue(baselineJson.startsWith("{"))
        assertTrue(defaultJson.startsWith("{"))
        assertTrue(mosaJson.startsWith("{"))
        assertTrue(serviceJson.startsWith("{"))

        val baseline = reader.readBaseline(parser.parse(baselineJson))
        val default = reader.readProfile(parser.parse(defaultJson))
        val mosa = reader.readProfile(parser.parse(mosaJson))
        val operator = reader.readProfile(parser.parse(serviceJson))

        assertEquals("privacy-gdpr-baseline", baseline.id)
        assertEquals("privacy-gdpr-default", default.id)
        assertEquals("mosa-privacy-gdpr-service", mosa.id)
        assertEquals("privacy-operator-service", operator.id)
    }

    @Test
    fun `chains parent hashes from baseline to default to mosa to service`() {
        val service =
            LegalService(
                operatorProfilePath = writeOperatorProfile(),
                completenessChecker = completenessChecker(complete = true),
            )

        val baselineJson = service.privacyBaselineCanonicalJson()
        val defaultJson = service.privacyDefaultCanonicalJson()
        val mosaJson = service.privacyMosaCanonicalJson()
        val serviceJson = service.privacyServiceCanonicalJson()

        val baseline = reader.readBaseline(parser.parse(baselineJson))
        val default = reader.readProfile(parser.parse(defaultJson))
        val mosa = reader.readProfile(parser.parse(mosaJson))
        val operator = reader.readProfile(parser.parse(serviceJson))

        assertEquals(baseline.id, default.parent.id)
        assertEquals(baseline.version, default.parent.version)
        assertEquals(default.id, mosa.parent.id)
        assertEquals(default.version, mosa.parent.version)
        assertEquals(mosa.id, operator.parent.id)
        assertEquals(mosa.version, operator.parent.version)
        assertEquals("sha256:${sha256(baselineJson)}", default.parent.hash)
        assertEquals("sha256:${sha256(defaultJson)}", mosa.parent.hash)
        assertEquals("sha256:${sha256(mosaJson)}", operator.parent.hash)
    }

    @Test
    fun `fails when operator profile file is missing`() {
        val missingPath = Files.createTempDirectory("legal-service-missing").resolve("mosa_privacy.yaml")
        val stdout = ByteArrayOutputStream()
        val exception =
            captureStdout(stdout) {
                assertFailsWith<IllegalStateException> {
                    LegalService(
                        operatorProfilePath = missingPath,
                        completenessChecker = completenessChecker(complete = false, skeletonYaml = "kind: profile"),
                    )
                }
            }

        assertTrue(stdout.toString(Charsets.UTF_8).contains("Suggested operator profile skeleton:"))
        assertTrue(exception.message?.contains("mosa_privacy.yaml") == true)
        assertTrue(exception.message?.contains(missingPath.toAbsolutePath().normalize().toString()) == true)
    }

    @Test
    fun `prints skeleton and fails when profile chain is incomplete`() {
        val stdout = ByteArrayOutputStream()

        val exception =
            captureStdout(stdout) {
                assertFailsWith<IllegalStateException> {
                    LegalService(
                        operatorProfilePath = writeOperatorProfile(),
                        completenessChecker = completenessChecker(complete = false, skeletonYaml = "kind: profile\nid: \"template\""),
                    )
                }
            }

        assertEquals("Legal profile chain is incomplete", exception.message)
        assertTrue(stdout.toString(Charsets.UTF_8).contains("Suggested operator profile skeleton:"))
        assertTrue(stdout.toString(Charsets.UTF_8).contains("id: \"template\""))
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun writeOperatorProfile(): Path {
        val path = Files.createTempDirectory("legal-service").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYaml())
        return path
    }

    private fun completenessChecker(
        complete: Boolean,
        skeletonYaml: String = "kind: profile",
    ): ClearTermsProfileChainCompletenessChecker {
        val checker = mockk<ClearTermsProfileChainCompletenessChecker>()
        every {
            checker.analyze(
                any(),
                any(),
            )
        } returns
            ClearTermsProfileChainCompletenessChecker.Result(
                complete = complete,
                missingPlaceholderKeys = emptyList(),
                unresolvedRequiredBlockIdsByLanguage = emptyMap(),
                skeletonYaml = skeletonYaml,
            )
        return checker
    }

    private fun captureStdout(
        stdout: ByteArrayOutputStream,
        block: () -> IllegalStateException,
    ): IllegalStateException {
        val previous = System.out
        System.setOut(PrintStream(stdout, true, Charsets.UTF_8))
        return try {
            block()
        } finally {
            System.setOut(previous)
        }
    }

    private fun operatorProfileYaml(): String =
        """
        kind: profile
        id: privacy-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-privacy-gdpr-service
          version: "1.0.0-draft2"
        overrides:
          values:
            SERVICE_DISPLAY_NAME: "Operator Service"
        """.trimIndent()
}
