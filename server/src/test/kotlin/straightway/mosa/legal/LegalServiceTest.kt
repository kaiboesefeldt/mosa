package straightway.mosa.legal

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import straightway.mosa.legal.clearterms.ClearTermsCanonicalJson
import straightway.mosa.legal.clearterms.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.ClearTermsHashProvider
import straightway.mosa.legal.clearterms.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.ClearTermsLocale
import straightway.mosa.legal.clearterms.ClearTermsLocaleProvider
import straightway.mosa.legal.clearterms.ClearTermsPlaceholderParser
import straightway.mosa.legal.clearterms.ClearTermsProfileChainCompletenessChecker
import straightway.mosa.legal.clearterms.ClearTermsProfileValidator
import straightway.mosa.legal.clearterms.ClearTermsRenderer
import straightway.mosa.legal.clearterms.ClearTermsTextEncoder
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.text.StringBuilder

class LegalServiceTest {
    private val parser = ClearTermsJsonParser()
    private val reader = ClearTermsDocumentReader()

    @Test
    fun `provides canonical json documents for privacy baseline default mosa and service`() {
        val service = createLegalService()

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
        assertEquals("mosa-privacy-fallback-service", operator.id)
    }

    @Test
    fun `chains parent hashes from baseline to default to mosa to service`() {
        val service = createLegalService()

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
    fun `creates fallback service profile when operator profile file is missing`() {
        val missingPath = Files.createTempDirectory("legal-service-missing").resolve("mosa_privacy.yaml")
        val stdout = ByteArrayOutputStream()
        val service =
            captureStdout(stdout) {
                createLegalService(
                    operatorProfilePath = missingPath,
                    completenessChecker = completenessChecker(complete = false, skeletonYaml = "kind: profile"),
                )
            }
        val mosaJson = service.privacyMosaCanonicalJson()
        val serviceJson = service.privacyServiceCanonicalJson()

        assertTrue(stdout.toString(Charsets.UTF_8).contains("Suggested operator profile skeleton:"))
        assertNotEquals(mosaJson, serviceJson)
    }

    @Test
    fun `prints skeleton and creates fallback profile when chain is incomplete`() {
        val stdout = ByteArrayOutputStream()
        val service =
            captureStdout(stdout) {
                createLegalService(
                    operatorProfilePath = writeOperatorProfile(),
                    completenessChecker = completenessChecker(complete = false, skeletonYaml = "kind: profile\nid: \"template\""),
                )
            }
        val mosaJson = service.privacyMosaCanonicalJson()
        val serviceJson = service.privacyServiceCanonicalJson()

        assertTrue(stdout.toString(Charsets.UTF_8).contains("Suggested operator profile skeleton:"))
        assertTrue(stdout.toString(Charsets.UTF_8).contains("id: \"template\""))
        assertNotEquals(mosaJson, serviceJson)
    }

    @Test
    fun `fallback service profile is complete when operator profile file is missing`() {
        val missingPath = Files.createTempDirectory("legal-service-missing-complete").resolve("mosa_privacy.yaml")
        val service = createLegalService(operatorProfilePath = missingPath)

        val baselineJson = service.privacyBaselineCanonicalJson()
        val defaultJson = service.privacyDefaultCanonicalJson()
        val mosaJson = service.privacyMosaCanonicalJson()
        val serviceJson = service.privacyServiceCanonicalJson()

        assertNotEquals(mosaJson, serviceJson)
        val analysis =
            ClearTermsProfileChainCompletenessChecker().analyze(
                baselineCanonicalJson = baselineJson,
                profileChainCanonicalJsons = listOf(defaultJson, mosaJson, serviceJson),
            )
        assertTrue(analysis.complete)
    }

    @Test
    fun `fallback chain from legal service is renderable for german`() =
        runTest {
            val missingPath = Files.createTempDirectory("legal-service-render").resolve("mosa_privacy.yaml")
            val service = createLegalService(operatorProfilePath = missingPath)

            val baselineJson = service.privacyBaselineCanonicalJson()
            val defaultJson = service.privacyDefaultCanonicalJson()
            val mosaJson = service.privacyMosaCanonicalJson()
            val serviceJson = service.privacyServiceCanonicalJson()

            val renderer =
                ClearTermsRenderer(
                    parser = ClearTermsJsonParser(),
                    hashProvider = Sha256HashProvider(),
                    textEncoder = Utf8TextEncoder(),
                    localeProvider = MinimalLocaleProvider(),
                )

            val rendered =
                renderer.renderLanguage(
                    baselineJson = baselineJson,
                    profileJsons = listOf(defaultJson, mosaJson, serviceJson),
                    languageTag = "de",
                )

            assertTrue(rendered.contains("Datenschutzerklärung"))
        }

    @Test
    fun `mosa privacy profile mentions snapshot freeze and restore workflow`() {
        val service = createLegalService()

        val mosaJson = service.privacyMosaCanonicalJson()

        assertTrue(mosaJson.contains("same pod", ignoreCase = true))
        assertTrue(mosaJson.contains("expires", ignoreCase = true))
        assertTrue(mosaJson.contains("data is lost", ignoreCase = true))
    }

    @Test
    fun `falls back when operator profile removes placeholder from final text`() {
        val path = Files.createTempDirectory("legal-service-invalid-unknown-placeholder").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithoutRetentionPlaceholder())
        val service =
            createLegalService(
                operatorProfilePath = path,
                completenessChecker = completenessChecker(complete = true),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))

        assertEquals("mosa-privacy-fallback-service", serviceProfile.id)
    }

    @Test
    fun `fallback keeps valid operator values when operator profile conversion fails`() {
        val path = Files.createTempDirectory("legal-service-invalid-parent").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithInvalidParentVersion())
        val service =
            createLegalService(
                operatorProfilePath = path,
                completenessChecker = completenessChecker(complete = true),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        val values = checkNotNull(serviceProfile.overrides.values)
        val orgControllerName =
            assertIs<straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.StringNode>(values.getValue("ORG_CONTROLLER_NAME"))
        val serviceDisplayName =
            assertIs<straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.StringNode>(values.getValue("SERVICE_DISPLAY_NAME"))

        assertEquals("mosa-privacy-fallback-service", serviceProfile.id)
        assertEquals("Operator Corp", orgControllerName.value)
        assertEquals("Operator Service", serviceDisplayName.value)
    }

    @Test
    fun `fails startup when operator profile contains placeholder value with wrong type`() {
        val path = Files.createTempDirectory("legal-service-invalid-value-type").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithInvalidValueType())

        val exception =
            assertFailsWith<IllegalStateException> {
                createLegalService(
                    operatorProfilePath = path,
                    completenessChecker = completenessChecker(complete = true),
                )
            }

        assertTrue(exception.message?.contains("invalid placeholder values") == true)
    }

    @Test
    fun `uses provided operator profile when it is complete and valid`() {
        val missingPath = Files.createTempDirectory("legal-service-fallback-seed").resolve("mosa_privacy.yaml")
        val fallbackSeedService =
            createLegalService(
                operatorProfilePath = missingPath,
                completenessChecker = completenessChecker(complete = true),
            )
        val fallbackProfile = reader.readProfile(parser.parse(fallbackSeedService.privacyServiceCanonicalJson()))
        val operatorValues = checkNotNull(fallbackProfile.overrides.values)

        val operatorPath = Files.createTempDirectory("legal-service-valid-operator").resolve("mosa_privacy.yaml")
        Files.writeString(operatorPath, validOperatorProfileYaml(operatorValues))

        val service =
            createLegalService(
                operatorProfilePath = operatorPath,
                completenessChecker = completenessChecker(complete = true),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        assertEquals("privacy-operator-service", serviceProfile.id)
        assertEquals("mosa-privacy-gdpr-service", serviceProfile.parent.id)
    }

    @Test
    fun `loads operator profile text overrides from sibling language fragment files`() {
        val missingPath = Files.createTempDirectory("legal-service-fragment-seed").resolve("mosa_privacy.yaml")
        val fallbackSeedService =
            createLegalService(
                operatorProfilePath = missingPath,
                completenessChecker = completenessChecker(complete = true),
            )
        val fallbackProfile = reader.readProfile(parser.parse(fallbackSeedService.privacyServiceCanonicalJson()))
        val operatorValues = checkNotNull(fallbackProfile.overrides.values)

        val operatorPath = Files.createTempDirectory("legal-service-fragment-operator").resolve("mosa_privacy.yaml")
        Files.writeString(operatorPath, validOperatorProfileYaml(operatorValues))
        Files.writeString(
            operatorPath.resolveSibling("mosa_privacy.de.yaml"),
            """
            overrides:
              texts:
                de:
                  replace:
                    retention-slot-service: "- Veröffentlichte Inhalte: bis auf Widerruf."
                en:
                  replace:
                    retention-slot-service: "- Published content: until revoked."
            """.trimIndent(),
        )

        val service =
            createLegalService(
                operatorProfilePath = operatorPath,
                completenessChecker = completenessChecker(complete = true),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        val deOverrides = checkNotNull(checkNotNull(serviceProfile.overrides.texts)["de"])

        assertEquals("privacy-operator-service", serviceProfile.id)
        assertEquals("- Veröffentlichte Inhalte: bis auf Widerruf.", deOverrides.replace["retention-slot-service"])
    }

    @Test
    fun `ignores sibling fragments when base operator file does not end with yaml`() {
        val missingPath = Files.createTempDirectory("legal-service-non-yaml-seed").resolve("mosa_privacy.yaml")
        val fallbackSeedService =
            createLegalService(
                operatorProfilePath = missingPath,
                completenessChecker = completenessChecker(complete = true),
            )
        val fallbackProfile = reader.readProfile(parser.parse(fallbackSeedService.privacyServiceCanonicalJson()))
        val operatorValues = checkNotNull(fallbackProfile.overrides.values)

        val path = Files.createTempDirectory("legal-service-non-yaml-operator").resolve("mosa_privacy.txt")
        Files.writeString(path, validOperatorProfileYaml(operatorValues))
        Files.writeString(path.resolveSibling("mosa_privacy.txt.de.yaml"), "overrides: [")

        val service =
            createLegalService(
                operatorProfilePath = path,
                completenessChecker = completenessChecker(complete = true),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        assertEquals("privacy-operator-service", serviceProfile.id)
    }

    @Test
    fun `fails startup when operator profile and fragment define same language`() {
        val path = Files.createTempDirectory("legal-service-duplicate-language").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithGermanTextOverride())
        Files.writeString(
            path.resolveSibling("mosa_privacy.de.yaml"),
            """
            overrides:
              texts:
                de:
                  replace:
                    retention-slot-service: "- Fragment-Text"
            """.trimIndent(),
        )

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                createLegalService(
                    operatorProfilePath = path,
                    completenessChecker = completenessChecker(complete = true),
                )
            }

        assertEquals("Language 'de' is defined more than once", exception.message)
    }

    @Test
    fun `falls back with empty extracted values when operator yaml cannot be parsed`() {
        val path = Files.createTempDirectory("legal-service-invalid-yaml").resolve("mosa_privacy.yaml")
        Files.writeString(path, "kind: profile\noverrides: [")

        val service =
            createLegalService(
                operatorProfilePath = path,
                completenessChecker = completenessChecker(complete = true),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        assertEquals("mosa-privacy-fallback-service", serviceProfile.id)
        assertTrue(checkNotNull(serviceProfile.overrides.values).isNotEmpty())
    }

    @Test
    fun `falls back when overrides values are not an object and ignores them`() {
        val path = Files.createTempDirectory("legal-service-invalid-values-node").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithNonObjectValues())

        val service =
            createLegalService(
                operatorProfilePath = path,
                completenessChecker = completenessChecker(complete = true),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        assertEquals("mosa-privacy-fallback-service", serviceProfile.id)
        val values = checkNotNull(serviceProfile.overrides.values)
        assertTrue("SERVICE_DISPLAY_NAME" !in values.keys)
    }

    @Test
    fun `fallback extracts textual number and null values from invalid operator profile`() {
        val path = Files.createTempDirectory("legal-service-invalid-mixed-values").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithMixedScalarValues())

        val service =
            createLegalService(
                operatorProfilePath = path,
                completenessChecker = completenessChecker(complete = true),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        val values = checkNotNull(serviceProfile.overrides.values)
        val serviceDisplayName =
            assertIs<straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.StringNode>(values.getValue("SERVICE_DISPLAY_NAME"))
        val retentionOperationalLogData =
            assertIs<straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.StringNode>(
                values.getValue("RETENTION_OPERATIONAL_LOG_DATA"),
            )

        assertEquals("mosa-privacy-fallback-service", serviceProfile.id)
        assertEquals("Operator Service", serviceDisplayName.value)
        assertEquals("infinite", retentionOperationalLogData.value)
        assertTrue("IRRELEVANT_NUMBER" !in values.keys)
    }

    @Test
    fun `fallback synthesizes defaults for all placeholder types and unresolved english text`() {
        val converter = ClearTermsYamlCanonicalJsonConverter()
        val customMandatoryConversion =
            converter.convert(
                baselineYaml = syntheticBaselineWithAllPlaceholderTypesAndUnresolvedEnglishText(),
                profileChainYamls =
                    listOf(
                        syntheticProfileYaml("synthetic-default", "synthetic-baseline"),
                        syntheticProfileYaml("synthetic-mosa", "synthetic-default"),
                    ),
            )
        val canonicalJsonConverter = mockk<ClearTermsYamlCanonicalJsonConverter>()
        every { canonicalJsonConverter.convert(any(), any()) } returns customMandatoryConversion
        val missingPath = Files.createTempDirectory("legal-service-synthetic-missing").resolve("mosa_privacy.yaml")

        val service =
            LegalService(
                canonicalJsonConverter = canonicalJsonConverter,
                operatorProfilePath = missingPath,
                completenessChecker = completenessChecker(complete = true),
                parser = ClearTermsJsonParser(),
                reader = ClearTermsDocumentReader(),
                profileValidator = ClearTermsProfileValidator(ClearTermsPlaceholderParser()),
                canonicalJson = ClearTermsCanonicalJson(),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        val values = checkNotNull(serviceProfile.overrides.values)
        val numberValue =
            assertIs<straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.NumberNode>(values.getValue("N_KEY"))
        val weekdayValue =
            assertIs<straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.StringNode>(values.getValue("W_KEY"))
        val durationValue =
            assertIs<straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.StringNode>(values.getValue("D_KEY"))
        val timeValue =
            assertIs<straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.StringNode>(values.getValue("T_KEY"))
        val stringValue =
            assertIs<straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.StringNode>(values.getValue("S_KEY"))
        val textOverrides = checkNotNull(serviceProfile.overrides.texts)
        val englishReplace = checkNotNull(textOverrides["en"]?.replace)

        assertEquals("mosa-privacy-fallback-service", serviceProfile.id)
        assertEquals(0.0, numberValue.value)
        assertEquals("mon", weekdayValue.value)
        assertEquals("infinite", durationValue.value)
        assertEquals("*-*-*", timeValue.value)
        assertEquals("not specified", stringValue.value)
        assertEquals("Not specified (unresolved-slot).", englishReplace["unresolved-slot"])
    }

    @Test
    fun `fallback uses german default replacement text for unresolved german block`() {
        val converter = ClearTermsYamlCanonicalJsonConverter()
        val customMandatoryConversion =
            converter.convert(
                baselineYaml = syntheticBaselineWithUnresolvedGermanText(),
                profileChainYamls =
                    listOf(
                        syntheticProfileYaml("synthetic-default", "synthetic-baseline"),
                        syntheticProfileYaml("synthetic-mosa", "synthetic-default"),
                    ),
            )
        val canonicalJsonConverter = mockk<ClearTermsYamlCanonicalJsonConverter>()
        every { canonicalJsonConverter.convert(any(), any()) } returns customMandatoryConversion
        val missingPath = Files.createTempDirectory("legal-service-synthetic-missing-de").resolve("mosa_privacy.yaml")

        val service =
            LegalService(
                canonicalJsonConverter = canonicalJsonConverter,
                operatorProfilePath = missingPath,
                completenessChecker = completenessChecker(complete = true),
                parser = ClearTermsJsonParser(),
                reader = ClearTermsDocumentReader(),
                profileValidator = ClearTermsProfileValidator(ClearTermsPlaceholderParser()),
                canonicalJson = ClearTermsCanonicalJson(),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        val textOverrides = checkNotNull(serviceProfile.overrides.texts)
        val germanReplace = checkNotNull(textOverrides["de"]?.replace)

        assertEquals("Nicht angegeben (unresolved-slot-de).", germanReplace["unresolved-slot-de"])
    }

    @Test
    fun `fallback uses empty operator values when full profile list is empty`() {
        val converter = ClearTermsYamlCanonicalJsonConverter()
        val mandatoryConversion =
            converter.convert(
                baselineYaml = syntheticBaselineWithRequiredStringPlaceholder(),
                profileChainYamls =
                    listOf(
                        syntheticProfileYaml("synthetic-default", "synthetic-baseline"),
                        syntheticProfileYaml("synthetic-mosa", "synthetic-default"),
                    ),
            )
        val fullConversionWithNoProfiles =
            ClearTermsYamlCanonicalJsonConverter.ConversionResult(
                baseline = mandatoryConversion.baseline,
                profiles = emptyList(),
            )
        val canonicalJsonConverter = mockk<ClearTermsYamlCanonicalJsonConverter>()
        every { canonicalJsonConverter.convert(any(), match { it.size == 2 }) } returns mandatoryConversion
        every { canonicalJsonConverter.convert(any(), match { it.size == 3 }) } returns fullConversionWithNoProfiles

        val operatorPath = Files.createTempDirectory("legal-service-empty-full-profiles").resolve("mosa_privacy.yaml")
        Files.writeString(operatorPath, operatorProfileYaml())

        val service =
            LegalService(
                canonicalJsonConverter = canonicalJsonConverter,
                operatorProfilePath = operatorPath,
                completenessChecker = completenessChecker(complete = true),
                parser = ClearTermsJsonParser(),
                reader = ClearTermsDocumentReader(),
                profileValidator = ClearTermsProfileValidator(ClearTermsPlaceholderParser()),
                canonicalJson = ClearTermsCanonicalJson(),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        assertEquals("mosa-privacy-fallback-service", serviceProfile.id)
        assertTrue(checkNotNull(serviceProfile.overrides.values).isNotEmpty())
    }

    @Test
    fun `fallback uses empty operator values when last full profile has no values`() {
        val converter = ClearTermsYamlCanonicalJsonConverter()
        val mandatoryConversion =
            converter.convert(
                baselineYaml = syntheticBaselineWithRequiredStringPlaceholder(),
                profileChainYamls =
                    listOf(
                        syntheticProfileYaml("synthetic-default", "synthetic-baseline"),
                        syntheticProfileYaml("synthetic-mosa", "synthetic-default"),
                    ),
            )
        val fullConversionWithSingleProfileWithoutValues =
            converter.convert(
                baselineYaml = syntheticBaselineWithRequiredStringPlaceholder(),
                profileChainYamls = listOf(syntheticProfileYaml("synthetic-operator-without-values", "synthetic-baseline")),
            )
        val canonicalJsonConverter = mockk<ClearTermsYamlCanonicalJsonConverter>()
        every { canonicalJsonConverter.convert(any(), match { it.size == 2 }) } returns mandatoryConversion
        every { canonicalJsonConverter.convert(any(), match { it.size == 3 }) } returns fullConversionWithSingleProfileWithoutValues

        val operatorPath = Files.createTempDirectory("legal-service-last-profile-no-values").resolve("mosa_privacy.yaml")
        Files.writeString(operatorPath, operatorProfileYaml())

        val service =
            LegalService(
                canonicalJsonConverter = canonicalJsonConverter,
                operatorProfilePath = operatorPath,
                completenessChecker = completenessChecker(complete = true),
                parser = ClearTermsJsonParser(),
                reader = ClearTermsDocumentReader(),
                profileValidator = ClearTermsProfileValidator(ClearTermsPlaceholderParser()),
                canonicalJson = ClearTermsCanonicalJson(),
            )

        val serviceProfile = reader.readProfile(parser.parse(service.privacyServiceCanonicalJson()))
        assertEquals("mosa-privacy-fallback-service", serviceProfile.id)
        assertTrue(checkNotNull(serviceProfile.overrides.values).isNotEmpty())
    }

    private fun createLegalService(
        operatorProfilePath: Path? = null,
        completenessChecker: ClearTermsProfileChainCompletenessChecker? = null,
    ) = LegalService(
        canonicalJsonConverter = ClearTermsYamlCanonicalJsonConverter(),
        operatorProfilePath = operatorProfilePath ?: writeOperatorProfile(),
        completenessChecker = completenessChecker ?: completenessChecker(complete = true),
        parser = ClearTermsJsonParser(),
        reader = ClearTermsDocumentReader(),
        profileValidator = ClearTermsProfileValidator(ClearTermsPlaceholderParser()),
        canonicalJson = ClearTermsCanonicalJson(),
    )

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
        block: () -> LegalService,
    ): LegalService {
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

    private fun operatorProfileYamlWithoutRetentionPlaceholder(): String =
        """
        kind: profile
        id: privacy-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-privacy-gdpr-service
          version: "1.0.0-draft2"
        overrides:
          texts:
            de:
              replace:
                retention-slot-service: |
                  - Veröffentlichte Inhalte: bis auf Widerruf.
            en:
              replace:
                retention-slot-service: |
                  - Published content: until revoked.
        """.trimIndent()

    private fun operatorProfileYamlWithInvalidParentVersion(): String =
        """
        kind: profile
        id: privacy-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-privacy-gdpr-service
          version: "1.0.0-draft1"
        overrides:
          values:
            ORG_CONTROLLER_NAME: "Operator Corp"
            SERVICE_DISPLAY_NAME: "Operator Service"
        """.trimIndent()

    private fun operatorProfileYamlWithInvalidValueType(): String =
        """
        kind: profile
        id: privacy-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-privacy-gdpr-service
          version: "1.0.0-draft2"
        overrides:
          values:
            DOC_EFFECTIVE_DATE: 42
        """.trimIndent()

    private fun operatorProfileYamlWithNonObjectValues(): String =
        """
        kind: profile
        id: privacy-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-privacy-gdpr-service
          version: "1.0.0-draft1"
        overrides:
          values: "not-an-object"
        """.trimIndent()

    private fun operatorProfileYamlWithMixedScalarValues(): String =
        """
        kind: profile
        id: privacy-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-privacy-gdpr-service
          version: "1.0.0-draft1"
        overrides:
          values:
            SERVICE_DISPLAY_NAME: "Operator Service"
            RETENTION_OPERATIONAL_LOG_DATA: null
            IRRELEVANT_NUMBER: 42
            IRRELEVANT_BOOLEAN: true
        """.trimIndent()

    private fun operatorProfileYamlWithGermanTextOverride(): String =
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
          texts:
            de:
              replace:
                retention-slot-service: "- Basis-Text"
            en:
              replace:
                retention-slot-service: "- Base text"
        """.trimIndent()

    private fun syntheticBaselineWithAllPlaceholderTypesAndUnresolvedEnglishText(): String =
        """
        kind: baseline
        id: synthetic-baseline
        version: "1.0.0"
        texts:
          en:
            - id: unresolved-slot
              text: null
            - id: placeholders
              text: "N={{number:N_KEY}}, W={{weekday:W_KEY}}, D={{duration:D_KEY}}, T={{time:T_KEY}}, S={{string:S_KEY}}"
        """.trimIndent()

    private fun syntheticBaselineWithUnresolvedGermanText(): String =
        """
        kind: baseline
        id: synthetic-baseline
        version: "1.0.0"
        texts:
          de:
            - id: unresolved-slot-de
              text: null
        """.trimIndent()

    private fun syntheticBaselineWithRequiredStringPlaceholder(): String =
        """
        kind: baseline
        id: synthetic-baseline
        version: "1.0.0"
        texts:
          en:
            - id: required
              text: "Required {{string:REQ_KEY}}"
        """.trimIndent()

    private fun syntheticProfileYaml(
        id: String,
        parentId: String,
    ): String =
        """
        kind: profile
        id: $id
        version: "1.0.0"
        parent:
          id: $parentId
          version: "1.0.0"
        overrides: {}
        """.trimIndent()

    private fun validOperatorProfileYaml(values: Map<String, straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode>): String {
        val builder = StringBuilder()
        builder.appendLine("kind: profile")
        builder.appendLine("id: privacy-operator-service")
        builder.appendLine("version: \"1.0.0-draft2\"")
        builder.appendLine("parent:")
        builder.appendLine("  id: mosa-privacy-gdpr-service")
        builder.appendLine("  version: \"1.0.0-draft2\"")
        builder.appendLine("overrides:")
        builder.appendLine("  values:")
        values.forEach { (key, value) ->
            val yamlValue =
                when (value) {
                    is straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.StringNode -> "\"${value.value.replace(
                        "\"",
                        "\\\"",
                    )}\""
                    is straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.NumberNode -> value.value.toString()
                    is straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.BooleanNode -> value.value.toString()
                    is straightway.mosa.legal.clearterms.ClearTermsJsonNode.ScalarNode.NullNode -> "null"
                }
            builder.appendLine("    $key: $yamlValue")
        }
        return builder.toString().trimEnd()
    }
}

private class Sha256HashProvider : ClearTermsHashProvider {
    override suspend fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}

private class Utf8TextEncoder : ClearTermsTextEncoder {
    override fun encode(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)
}

private class MinimalLocaleProvider : ClearTermsLocaleProvider {
    override fun resolve(languageTag: String): ClearTermsLocale =
        ClearTermsLocale(
            language = "de",
            months =
                listOf(
                    "Januar",
                    "Februar",
                    "März",
                    "April",
                    "Mai",
                    "Juni",
                    "Juli",
                    "August",
                    "September",
                    "Oktober",
                    "November",
                    "Dezember",
                ),
            weekdays =
                mapOf(
                    "mon" to "Montag",
                    "tue" to "Dienstag",
                    "wed" to "Mittwoch",
                    "thu" to "Donnerstag",
                    "fri" to "Freitag",
                    "sat" to "Samstag",
                    "sun" to "Sonntag",
                ),
            decimalSeparator = ',',
            groupingSeparator = '.',
            atWord = "um",
            patterns =
                ClearTermsLocale.Patterns(
                    onFullDate = "{day}. {month} {year}",
                    inMonthYear = "{month} {year}",
                    onDayOfEveryMonthInYear = "{day}. jedes Monats im Jahr {year}",
                    inYear = "im Jahr {year}",
                    onMonthDayEveryYear = "am {day}. {month} jedes Jahres",
                    everyDayInMonth = "jeden Tag im {month}",
                    onDayOfEveryMonth = "am {day}. jedes Monats",
                    everyDay = "jeden Tag",
                    onLastDayOfMonth = "am letzten Tag von {month} {year}",
                    onLastDayOfMonthEveryYear = "am letzten Tag von {month} jedes Jahres",
                    onLastDayOfEveryMonthInYear = "am letzten Tag jedes Monats im Jahr {year}",
                    onLastDayOfEveryMonth = "am letzten Tag jedes Monats",
                ),
        )
}
