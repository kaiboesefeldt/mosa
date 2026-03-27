package straightway.mosa.legal

import io.mockk.every
import io.mockk.mockk
import straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegalChainValidatorTest {
    @Test
    fun `validate operator profile for startup fails when operator file is missing`() {
        val source = createLegalProfileSource()
        val validator = createLegalChainValidator(completenessChecker(complete = true), source)
        val path = Files.createTempDirectory("legal-validator-missing").resolve("mosa_privacy.yaml")

        val exception =
            assertFailsWith<IllegalStateException> {
                validator.validateOperatorProfileForStartup(
                    kind = LegalDocumentKind.Privacy,
                    profilePath = path,
                    mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
                )
            }

        assertTrue(exception.message?.contains("operator profile file is missing") == true)
    }

    @Test
    fun `validate operator profile for startup reports invalid conversion`() {
        val source = createLegalProfileSource()
        val validator = createLegalChainValidator(completenessChecker(complete = true), source)
        val path = Files.createTempDirectory("legal-validator-invalid-conversion").resolve("mosa_privacy.yaml")
        Files.writeString(path, "kind: profile\noverrides: [")

        val exception =
            assertFailsWith<IllegalStateException> {
                validator.validateOperatorProfileForStartup(
                    kind = LegalDocumentKind.Privacy,
                    profilePath = path,
                    mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
                )
            }

        assertTrue(exception.message?.contains("could not be converted") == true)
    }

    @Test
    fun `validate operator profile for startup reports incomplete chain details`() {
        val source = createLegalProfileSource()
        val validator =
            createLegalChainValidator(
                completenessChecker(
                    complete = false,
                    missingPlaceholderKeys = listOf("REQ_KEY"),
                    unresolvedRequiredBlockIdsByLanguage = mapOf("de" to listOf("slot-1")),
                ),
                source,
            )
        val path = Files.createTempDirectory("legal-validator-incomplete").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYaml())

        val exception =
            assertFailsWith<IllegalStateException> {
                validator.validateOperatorProfileForStartup(
                    kind = LegalDocumentKind.Privacy,
                    profilePath = path,
                    mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
                )
            }

        assertTrue(exception.message?.contains("missing placeholder keys: REQ_KEY") == true)
        assertTrue(exception.message?.contains("unresolved block ids: de:slot-1") == true)
    }

    @Test
    fun `validate operator profile for startup reports invalid placeholder value types`() {
        val source = createLegalProfileSource()
        val validator = createLegalChainValidator(completenessChecker(complete = true), source)
        val path = Files.createTempDirectory("legal-validator-invalid-type").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithInvalidValueType())

        val exception =
            assertFailsWith<IllegalStateException> {
                validator.validateOperatorProfileForStartup(
                    kind = LegalDocumentKind.Privacy,
                    profilePath = path,
                    mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
                )
            }

        assertTrue(exception.message?.contains("invalid placeholder values") == true)
    }

    @Test
    fun `validate operator profile for startup reports missing required placeholder values`() {
        val source = createLegalProfileSource()
        val validator = createLegalChainValidator(completenessChecker(complete = true), source)
        val path = Files.createTempDirectory("legal-validator-missing-values").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithNullValue())

        val exception =
            assertFailsWith<IllegalStateException> {
                validator.validateOperatorProfileForStartup(
                    kind = LegalDocumentKind.Privacy,
                    profilePath = path,
                    mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
                )
            }

        assertTrue(exception.message?.contains("invalid placeholder values") == true)
    }

    @Test
    fun `require valid full chain fails for incomplete chain`() {
        val source = createLegalProfileSource()
        val validator = createLegalChainValidator(completenessChecker(complete = false), source)
        val path = Files.createTempDirectory("legal-validator-fullchain-incomplete").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYaml())
        val conversion =
            source.convertFullChain(
                mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
                operatorYaml = Files.readString(path),
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                validator.requireValidFullChain(
                    fullChainContext = validator.createFullChainContext(conversion),
                    affectedProfilePath = path,
                )
            }

        assertTrue(exception.message?.contains("is incomplete") == true)
    }

    @Test
    fun `require valid full chain fails for invalid value types`() {
        val source = createLegalProfileSource()
        val validator = createLegalChainValidator(completenessChecker(complete = true), source)
        val path = Files.createTempDirectory("legal-validator-fullchain-invalid-type").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithInvalidValueType())
        val conversion =
            source.convertFullChain(
                mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
                operatorYaml = Files.readString(path),
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                validator.requireValidFullChain(
                    fullChainContext = validator.createFullChainContext(conversion),
                    affectedProfilePath = path,
                )
            }

        assertTrue(exception.message?.contains("invalid placeholder values") == true)
    }

    @Test
    fun `require valid full chain fails for missing required placeholder values`() {
        val source = createLegalProfileSource()
        val validator = createLegalChainValidator(completenessChecker(complete = true), source)
        val path = Files.createTempDirectory("legal-validator-fullchain-missing-values").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithNullValue())
        val conversion =
            source.convertFullChain(
                mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
                operatorYaml = Files.readString(path),
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                validator.requireValidFullChain(
                    fullChainContext = validator.createFullChainContext(conversion),
                    affectedProfilePath = path,
                )
            }

        assertTrue(exception.message?.contains("invalid placeholder values") == true)
    }

    @Test
    fun `validate operator profile for startup succeeds for complete valid profile`() {
        val source = createLegalProfileSource()
        val validator = createLegalChainValidator(completenessChecker(complete = true), source)
        val path = Files.createTempDirectory("legal-validator-valid-startup").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYaml())

        validator.validateOperatorProfileForStartup(
            kind = LegalDocumentKind.Privacy,
            profilePath = path,
            mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
        )
    }

    @Test
    fun `require valid full chain succeeds for complete valid profile`() {
        val source = createLegalProfileSource()
        val validator = createLegalChainValidator(completenessChecker(complete = true), source)
        val path = Files.createTempDirectory("legal-validator-valid-fullchain").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYaml())
        val conversion =
            source.convertFullChain(
                mandatoryContext = source.loadMandatoryChainContext(LegalDocumentKind.Privacy),
                operatorYaml = Files.readString(path),
            )

        validator.requireValidFullChain(
            fullChainContext = validator.createFullChainContext(conversion),
            affectedProfilePath = path,
        )
    }

    private fun completenessChecker(
        complete: Boolean,
        missingPlaceholderKeys: List<String> = emptyList(),
        unresolvedRequiredBlockIdsByLanguage: Map<String, List<String>> = emptyMap(),
    ): straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker {
        val checker = mockk<straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker>()
        every { checker.analyze(any(), any()) } returns
            straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker.Result(
                complete = complete,
                missingPlaceholderKeys = missingPlaceholderKeys,
                unresolvedRequiredBlockIdsByLanguage = unresolvedRequiredBlockIdsByLanguage,
                skeletonYaml = "kind: profile\noverrides: {}",
            )
        return checker
    }
}
