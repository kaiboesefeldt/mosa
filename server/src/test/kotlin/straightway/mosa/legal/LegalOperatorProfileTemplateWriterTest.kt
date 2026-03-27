package straightway.mosa.legal

import io.mockk.every
import io.mockk.mockk
import straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalOperatorProfileTemplateWriterTest {
    @Test
    fun `create missing operator profile file writes expected template`() {
        val writer = createWriter("kind: profile\noverrides:\n  values:\n    EXAMPLE: null")
        val path = Files.createTempDirectory("legal-template-writer").resolve("mosa_privacy.yaml")

        writer.createMissingOperatorProfileFile(
            profilePath = path,
            mandatoryContext = mandatoryContext(),
            kind = LegalDocumentKind.Privacy,
        )

        val written = Files.readString(path)
        assertTrue(written.contains("""id: "privacy-operator-service""""))
        assertTrue(written.contains("""version: "1.0.0-draft2""""))
        assertTrue(written.contains("""  id: "service""""))
        assertTrue(written.contains("EXAMPLE: null"))
    }

    @Test
    fun `create missing operator profile file if needed does nothing when file already exists`() {
        val writer = createWriter("kind: profile\noverrides:\n  values:\n    EXAMPLE: null")
        val path = Files.createTempDirectory("legal-template-existing").resolve("mosa_terms.yaml")
        Files.writeString(path, "already-present")
        val createdPaths = mutableListOf<Path>()

        writer.createMissingOperatorProfileFileIfNeeded(
            profilePath = path,
            mandatoryContext = mandatoryContext(),
            kind = LegalDocumentKind.Terms,
            createdPaths = createdPaths,
        )

        assertEquals("already-present", Files.readString(path))
        assertTrue(createdPaths.isEmpty())
    }

    @Test
    fun `create missing operator profile file if needed creates file in current directory when parent is absent`() {
        val writer = createWriter("kind: profile\noverrides:\n  values:\n    EXAMPLE: null")
        val relativePath = Path.of("legal-template-${System.nanoTime()}.yaml")
        val createdPaths = mutableListOf<Path>()

        try {
            writer.createMissingOperatorProfileFileIfNeeded(
                profilePath = relativePath,
                mandatoryContext = mandatoryContext(),
                kind = LegalDocumentKind.Terms,
                createdPaths = createdPaths,
            )

            assertTrue(Files.exists(relativePath))
            assertFalse(createdPaths.isEmpty())
        } finally {
            Files.deleteIfExists(relativePath)
        }
    }

    private fun createWriter(skeletonYaml: String): LegalOperatorProfileTemplateWriter {
        val checker = mockk<straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker>()
        every { checker.analyze(any(), any()) } returns
            straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker.Result(
                complete = false,
                missingPlaceholderKeys = emptyList(),
                unresolvedRequiredBlockIdsByLanguage = emptyMap(),
                skeletonYaml = skeletonYaml,
            )
        return LegalOperatorProfileTemplateWriter(checker)
    }

    private fun mandatoryContext() =
        MandatoryChainContext(
            baselineYaml = "kind: baseline",
            profileYamls = listOf("kind: profile"),
            conversion = createLegalProfileSource().loadMandatoryChainContext(LegalDocumentKind.Privacy).conversion,
            profileCanonicalJsons = listOf("""{"id":"default"}"""),
            lastProfile =
                ProfileMetadata(
                    id = "service",
                    version = "1.0.0",
                    parent = ArtifactReference("default", "1.0.0"),
                ),
            availableProfileVersions = emptyMap(),
        )
}
