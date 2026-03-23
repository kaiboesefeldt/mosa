package straightway.mosa.legal

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import straightway.mosa.legal.clearterms.ClearTermsHashProvider
import straightway.mosa.legal.clearterms.ClearTermsLocale
import straightway.mosa.legal.clearterms.ClearTermsLocaleProvider
import straightway.mosa.legal.clearterms.ClearTermsRenderer
import straightway.mosa.legal.clearterms.ClearTermsTextEncoder
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.parse.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.validation.ClearTermsProfileChainCompletenessChecker
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LegalServiceTest {
    private val parser = ClearTermsJsonParser()
    private val reader = ClearTermsDocumentReader()
    private val yamlMapper = ObjectMapper(YAMLFactory())

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
        assertEquals("privacy-operator-service", operator.id)
    }

    @Test
    fun `provides document chain responses for privacy and terms`() {
        val service = createLegalService()

        val privacyResponse = service.privacyDocumentChainResponse()
        val termsResponse = service.termsDocumentChainResponse()

        assertEquals("privacy-gdpr-baseline", privacyResponse.baseline["id"]?.jsonPrimitive?.content)
        assertEquals(3, privacyResponse.profiles.size)
        assertEquals("terms-baseline", termsResponse.baseline["id"]?.jsonPrimitive?.content)
        assertEquals(3, termsResponse.profiles.size)
    }

    @Test
    fun `provides canonical json documents for terms baseline default mosa and operator`() {
        val service = createLegalService()

        val baselineJson = service.termsBaselineCanonicalJson()
        val defaultJson = service.termsDefaultCanonicalJson()
        val mosaJson = service.termsMosaCanonicalJson()
        val operatorJson = service.termsOperatorCanonicalJson()

        assertTrue(baselineJson.startsWith("{"))
        assertTrue(defaultJson.startsWith("{"))
        assertTrue(mosaJson.startsWith("{"))
        assertTrue(operatorJson.startsWith("{"))

        val baseline = reader.readBaseline(parser.parse(baselineJson))
        val default = reader.readProfile(parser.parse(defaultJson))
        val mosa = reader.readProfile(parser.parse(mosaJson))
        val operator = reader.readProfile(parser.parse(operatorJson))

        assertEquals("terms-baseline", baseline.id)
        assertEquals("terms-default", default.id)
        assertEquals("mosa-terms-service", mosa.id)
        assertEquals("terms-operator-service", operator.id)
    }

    @Test
    fun `chains parent hashes from terms baseline to default to mosa to operator`() {
        val service = createLegalService()

        val baselineJson = service.termsBaselineCanonicalJson()
        val defaultJson = service.termsDefaultCanonicalJson()
        val mosaJson = service.termsMosaCanonicalJson()
        val operatorJson = service.termsOperatorCanonicalJson()

        val baseline = reader.readBaseline(parser.parse(baselineJson))
        val default = reader.readProfile(parser.parse(defaultJson))
        val mosa = reader.readProfile(parser.parse(mosaJson))
        val operator = reader.readProfile(parser.parse(operatorJson))

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
    fun `terms chain from legal service is renderable for german`() =
        runTest {
            val service = createLegalService()

            val renderer =
                ClearTermsRenderer(
                    parser = ClearTermsJsonParser(),
                    hashProvider = Sha256HashProvider(),
                    textEncoder = Utf8TextEncoder(),
                    localeProvider = MinimalLocaleProvider(),
                )

            val rendered =
                renderer
                    .renderLanguage(
                        baselineJson = service.termsBaselineCanonicalJson(),
                        profileJsons =
                            listOf(
                                service.termsDefaultCanonicalJson(),
                                service.termsMosaCanonicalJson(),
                                service.termsOperatorCanonicalJson(),
                            ),
                        languageTag = "de",
                    ).text

            assertTrue(rendered.contains("Allgemeine Geschäftsbedingungen"))
        }

    @Test
    fun `uses provided terms operator profile when it is complete and valid`() {
        val operatorPath = Files.createTempDirectory("legal-service-valid-terms-operator").resolve("mosa_terms.yaml")
        Files.writeString(operatorPath, validTermsOperatorProfileYaml())

        val service =
            createLegalService(
                termsOperatorProfilePath = operatorPath,
            )

        val operatorProfile = reader.readProfile(parser.parse(service.termsOperatorCanonicalJson()))
        val values = checkNotNull(operatorProfile.overrides.values)
        val controllerName =
            assertIs<straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode.ScalarNode.StringNode>(
                values.getValue("ORG_CONTROLLER_NAME"),
            )

        assertEquals("terms-operator-service", operatorProfile.id)
        assertEquals("mosa-terms-service", operatorProfile.parent.id)
        assertEquals("Operator Corp", controllerName.value)
    }

    @Test
    fun `validate startup fails when terms operator profile file is missing`() {
        val missingPath = Files.createTempDirectory("legal-service-startup-missing-terms").resolve("mosa_terms.yaml")

        val exception =
            assertFailsWith<IllegalStateException> {
                createLegalService(
                    termsOperatorProfilePath = missingPath,
                    completenessChecker = completenessChecker(complete = false),
                ).validateStartup()
            }

        assertTrue(exception.message?.contains("missing or incomplete")!!)
    }

    @Test
    fun `validate startup reports missing placeholder keys for incomplete operator profile`() {
        val path = Files.createTempDirectory("legal-service-startup-missing-placeholder").resolve("mosa_terms.yaml")
        Files.writeString(
            path,
            """
            kind: profile
            id: terms-operator-service
            version: "1.0.0-draft2"
            parent:
              id: mosa-terms-service
              version: "1.0.0-draft1"
            overrides:
              values:
                CONTACT_CONTROLLER_EMAIL: "legal@operator.example"
                DOC_EFFECTIVE_DATE: "2026-03-13T00:00:00Z"
              texts:
                de:
                  replace:
                    service-purpose-slot-service: "Kurzbeschreibung"
                    account-details-slot-service: "Kontenbeschreibung"
                    user-content-visibility-slot-service: "Sichtbarkeit"
                    service-rights-slot-service: "Betriebsregeln"
                    user-data-control-slot-service: "Datenkontrolle"
            """.trimIndent(),
        )

        val exception =
            assertFailsWith<IllegalStateException> {
                createLegalService(
                    operatorProfilePath = writeOperatorProfile(),
                    termsOperatorProfilePath = path,
                ).validateStartup()
            }

        assertTrue(exception.message?.contains("ORG_CONTROLLER_NAME")!!)
    }

    @Test
    fun `validate startup creates missing privacy operator profile file without touching terms file`() {
        val directory = Files.createTempDirectory("legal-service-create-missing-privacy")
        val missingPrivacyPath = directory.resolve("mosa_privacy.yaml")
        val existingTermsPath = directory.resolve("mosa_terms.yaml")
        Files.writeString(existingTermsPath, validTermsOperatorProfileYaml())
        val originalTermsContent = Files.readString(existingTermsPath)

        val exception =
            assertFailsWith<IllegalStateException> {
                createLegalService(
                    operatorProfilePath = missingPrivacyPath,
                    termsOperatorProfilePath = existingTermsPath,
                    completenessChecker = completenessChecker(complete = true),
                ).validateStartup()
            }

        assertTrue(Files.exists(missingPrivacyPath))
        assertEquals(originalTermsContent, Files.readString(existingTermsPath))
        assertTrue(
            exception.message?.contains(missingPrivacyPath.toAbsolutePath().normalize().toString())!!,
        )
    }

    @Test
    fun `validate startup creates missing terms operator profile file without touching privacy file`() {
        val directory = Files.createTempDirectory("legal-service-create-missing-terms")
        val existingPrivacyPath = directory.resolve("mosa_privacy.yaml")
        val missingTermsPath = directory.resolve("mosa_terms.yaml")
        Files.writeString(existingPrivacyPath, operatorProfileYaml())
        val originalPrivacyContent = Files.readString(existingPrivacyPath)

        val exception =
            assertFailsWith<IllegalStateException> {
                createLegalService(
                    operatorProfilePath = existingPrivacyPath,
                    termsOperatorProfilePath = missingTermsPath,
                    completenessChecker = completenessChecker(complete = true),
                ).validateStartup()
            }

        assertTrue(Files.exists(missingTermsPath))
        assertEquals(originalPrivacyContent, Files.readString(existingPrivacyPath))
        assertTrue(
            exception.message?.contains(missingTermsPath.toAbsolutePath().normalize().toString())!!,
        )
    }

    @Test
    fun `creates privacy template with null values and expected content comments`() {
        val directory = Files.createTempDirectory("legal-service-privacy-template")
        val missingPrivacyPath = directory.resolve("mosa_privacy.yaml")

        assertFailsWith<IllegalStateException> {
            createLegalService(
                operatorProfilePath = missingPrivacyPath,
                termsOperatorProfilePath =
                    directory.resolve("mosa_terms.yaml").also {
                        Files.writeString(it, validTermsOperatorProfileYaml())
                    },
                completenessChecker = completenessChecker(complete = true),
            ).privacyServiceCanonicalJson()
        }

        val templateYaml = Files.readString(missingPrivacyPath)
        val templateNode = yamlMapper.readTree(templateYaml)

        assertEquals("privacy-operator-service", templateNode.path("id").asText())
        assertEquals("1.0.0-draft2", templateNode.path("version").asText())
        assertEquals("mosa-privacy-gdpr-service", templateNode.path("parent").path("id").asText())
        assertTrue(
            templateNode
                .path("overrides")
                .path("values")
                .path("ORG_CONTROLLER_NAME")
                .isNull,
        )
        assertTrue(
            templateYaml.contains(
                "service-description-slot-service: null # TODO: Brief description of the service's core functions.",
            ),
        )
    }

    @Test
    fun `creates terms template with null values and expected content comments`() {
        val directory = Files.createTempDirectory("legal-service-terms-template")
        val missingTermsPath = directory.resolve("mosa_terms.yaml")

        assertFailsWith<IllegalStateException> {
            createLegalService(
                operatorProfilePath =
                    directory.resolve("mosa_privacy.yaml").also {
                        Files.writeString(it, operatorProfileYaml())
                    },
                termsOperatorProfilePath = missingTermsPath,
                completenessChecker = completenessChecker(complete = true),
            ).termsOperatorCanonicalJson()
        }

        val templateYaml = Files.readString(missingTermsPath)
        val templateNode = yamlMapper.readTree(templateYaml)

        assertEquals("terms-operator-service", templateNode.path("id").asText())
        assertEquals("1.0.0-draft2", templateNode.path("version").asText())
        assertEquals("mosa-terms-service", templateNode.path("parent").path("id").asText())
        assertTrue(
            templateNode
                .path("overrides")
                .path("values")
                .path("ORG_CONTROLLER_NAME")
                .isNull,
        )
        assertTrue(
            templateYaml.contains(
                "service-purpose-slot-service: null # TODO: Brief description of the service's core functions.",
            ),
        )
    }

    @Test
    fun `validate startup succeeds after generated templates are filled minimally`() {
        val directory = Files.createTempDirectory("legal-service-fill-generated-templates")
        val privacyPath = directory.resolve("mosa_privacy.yaml")
        val termsPath = directory.resolve("mosa_terms.yaml")

        assertFailsWith<IllegalStateException> {
            createLegalService(
                operatorProfilePath = privacyPath,
                termsOperatorProfilePath = termsPath,
                completenessChecker = completenessChecker(complete = true),
            ).validateStartup()
        }

        Files.writeString(privacyPath, minimallyFilledTemplateYaml(Files.readString(privacyPath)))
        Files.writeString(termsPath, minimallyFilledTemplateYaml(Files.readString(termsPath)))

        createLegalService(
            operatorProfilePath = privacyPath,
            termsOperatorProfilePath = termsPath,
            completenessChecker = completenessChecker(complete = true),
        ).validateStartup()
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
    fun `fails when privacy operator profile file is missing and logs error without stdout yaml dump`() {
        val missingPath = Files.createTempDirectory("legal-service-missing").resolve("mosa_privacy.yaml")
        val stdout = ByteArrayOutputStream()
        val logMessages =
            captureLegalServiceLogs {
                assertFailsWith<IllegalStateException> {
                    captureStdout(stdout) {
                        createLegalService(
                            operatorProfilePath = missingPath,
                            completenessChecker = completenessChecker(complete = false, skeletonYaml = "kind: profile"),
                        ).privacyServiceCanonicalJson()
                    }
                }
            }

        assertTrue(stdout.toString(Charsets.UTF_8).contains("Suggested operator profile example:").not())
        assertTrue(stdout.toString(Charsets.UTF_8).contains("Fill-in hints:").not())
        assertTrue(logMessages.any { it.contains(missingPath.toAbsolutePath().normalize().toString()) })
        assertTrue(logMessages.any { it.contains("Review and complete") })
    }

    @Test
    fun `fails when privacy operator profile chain is incomplete and logs error without stdout yaml dump`() {
        val path = writeOperatorProfile()
        val stdout = ByteArrayOutputStream()
        val logMessages =
            captureLegalServiceLogs {
                assertFailsWith<IllegalStateException> {
                    captureStdout(stdout) {
                        createLegalService(
                            operatorProfilePath = path,
                            completenessChecker = completenessChecker(complete = false, skeletonYaml = "kind: profile\nid: \"template\""),
                        ).privacyServiceCanonicalJson()
                    }
                }
            }

        assertTrue(stdout.toString(Charsets.UTF_8).contains("Suggested operator profile example:").not())
        assertTrue(stdout.toString(Charsets.UTF_8).contains("Fill-in hints:").not())
        assertTrue(logMessages.any { it.contains(path.toAbsolutePath().normalize().toString()) })
        assertTrue(logMessages.any { it.contains("validation failed") })
    }

    @Test
    fun `fails when terms operator profile file is missing and logs error without stdout yaml dump`() {
        val missingPath = Files.createTempDirectory("legal-service-missing-terms").resolve("mosa_terms.yaml")
        val stdout = ByteArrayOutputStream()
        val logMessages =
            captureLegalServiceLogs {
                assertFailsWith<IllegalStateException> {
                    captureStdout(stdout) {
                        createLegalService(
                            termsOperatorProfilePath = missingPath,
                            completenessChecker = completenessChecker(complete = false, skeletonYaml = "kind: profile"),
                        ).termsOperatorCanonicalJson()
                    }
                }
            }

        assertTrue(stdout.toString(Charsets.UTF_8).contains("Suggested operator profile example:").not())
        assertTrue(stdout.toString(Charsets.UTF_8).contains("Fill-in hints:").not())
        assertTrue(logMessages.any { it.contains(missingPath.toAbsolutePath().normalize().toString()) })
        assertTrue(logMessages.any { it.contains("Review and complete") })
    }

    @Test
    fun `fails when terms operator profile chain is incomplete and logs error without stdout yaml dump`() {
        val path = writeTermsOperatorProfile()
        val stdout = ByteArrayOutputStream()
        val logMessages =
            captureLegalServiceLogs {
                assertFailsWith<IllegalStateException> {
                    captureStdout(stdout) {
                        createLegalService(
                            termsOperatorProfilePath = path,
                            completenessChecker =
                                completenessChecker(
                                    complete = false,
                                    skeletonYaml = "kind: profile\nid: \"terms-template\"",
                                ),
                        ).termsOperatorCanonicalJson()
                    }
                }
            }

        assertTrue(stdout.toString(Charsets.UTF_8).contains("Suggested operator profile example:").not())
        assertTrue(stdout.toString(Charsets.UTF_8).contains("Fill-in hints:").not())
        assertTrue(logMessages.any { it.contains(path.toAbsolutePath().normalize().toString()) })
        assertTrue(logMessages.any { it.contains("validation failed") })
    }

    @Test
    fun `fallback chain from legal service is renderable for german when operator yaml is invalid`() =
        runTest {
            val path = Files.createTempDirectory("legal-service-render").resolve("mosa_privacy.yaml")
            Files.writeString(path, "kind: profile\noverrides: [")
            val service = createLegalService(operatorProfilePath = path)

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
                renderer
                    .renderLanguage(
                        baselineJson = baselineJson,
                        profileJsons = listOf(defaultJson, mosaJson, serviceJson),
                        languageTag = "de",
                    ).text

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
    fun `fails when operator profile removes placeholder from final text`() {
        val path = Files.createTempDirectory("legal-service-invalid-unknown-placeholder").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithoutRetentionPlaceholder())
        val exception =
            assertFailsWith<IllegalStateException> {
                createLegalService(
                    operatorProfilePath = path,
                    completenessChecker = completenessChecker(complete = true),
                ).privacyServiceCanonicalJson()
            }

        assertTrue(exception.message?.contains("missing or incomplete")!!)
    }

    @Test
    fun `fails when existing privacy operator profile still contains null values and mentions file path`() {
        val path = Files.createTempDirectory("legal-service-null-privacy-value").resolve("mosa_privacy.yaml")
        Files.writeString(path, operatorProfileYamlWithNullValue())

        val exception =
            assertFailsWith<IllegalStateException> {
                createLegalService(
                    operatorProfilePath = path,
                    completenessChecker = completenessChecker(complete = true),
                ).privacyServiceCanonicalJson()
            }

        assertTrue(exception.message?.contains(path.toAbsolutePath().normalize().toString())!!)
        assertTrue(exception.message?.contains("missing or incomplete")!!)
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
            assertIs<straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode.ScalarNode.StringNode>(
                values.getValue("ORG_CONTROLLER_NAME"),
            )
        val serviceDisplayName =
            assertIs<straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode.ScalarNode.StringNode>(
                values.getValue("SERVICE_DISPLAY_NAME"),
            )

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
                ).privacyServiceCanonicalJson()
            }

        assertTrue(exception.message?.contains("invalid placeholder values")!!)
    }

    @Test
    fun `uses provided operator profile when it is complete and valid`() {
        val operatorPath = Files.createTempDirectory("legal-service-valid-operator").resolve("mosa_privacy.yaml")
        Files.writeString(operatorPath, operatorProfileYaml())

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
        val operatorPath = Files.createTempDirectory("legal-service-fragment-operator").resolve("mosa_privacy.yaml")
        Files.writeString(operatorPath, operatorProfileYaml())
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
        val path = Files.createTempDirectory("legal-service-non-yaml-operator").resolve("mosa_privacy.txt")
        Files.writeString(path, operatorProfileYaml())
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
                ).privacyServiceCanonicalJson()
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
            assertIs<straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode.ScalarNode.StringNode>(
                values.getValue("SERVICE_DISPLAY_NAME"),
            )
        val retentionOperationalLogData =
            assertIs<straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode.ScalarNode.StringNode>(
                values.getValue("RETENTION_OPERATIONAL_LOG_DATA"),
            )

        assertEquals("mosa-privacy-fallback-service", serviceProfile.id)
        assertEquals("Operator Service", serviceDisplayName.value)
        assertEquals("infinite", retentionOperationalLogData.value)
        assertTrue("IRRELEVANT_NUMBER" !in values.keys)
    }

    @Test
    fun `logs example profile defaults for all placeholder types and unresolved english text`() {
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
        val exception =
            assertFailsWith<IllegalStateException> {
                createLegalService(
                    operatorProfilePath = missingPath,
                    termsOperatorProfilePath = writeTermsOperatorProfile(),
                    completenessChecker = completenessChecker(complete = true),
                    canonicalJsonConverter = canonicalJsonConverter,
                ).privacyServiceCanonicalJson()
            }

        assertTrue(exception.message?.contains("missing or incomplete")!!)
    }

    @Test
    fun `logs unresolved german block as structured error without stdout yaml dump`() {
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
        val stdout = ByteArrayOutputStream()

        val logMessages =
            captureLegalServiceLogs {
                assertFailsWith<IllegalStateException> {
                    captureStdout(stdout) {
                        createLegalService(
                            operatorProfilePath = missingPath,
                            termsOperatorProfilePath = writeTermsOperatorProfile(),
                            completenessChecker = completenessChecker(complete = true),
                            canonicalJsonConverter = canonicalJsonConverter,
                        ).privacyServiceCanonicalJson()
                    }
                }
            }

        assertTrue(stdout.toString(Charsets.UTF_8).contains("Suggested operator profile example:").not())
        assertTrue(stdout.toString(Charsets.UTF_8).contains("Fill-in hints:").not())
        assertTrue(logMessages.any { it.contains(missingPath.toAbsolutePath().normalize().toString()) })
    }

    @Test
    fun `fails when full profile list is empty`() {
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
            createLegalService(
                operatorProfilePath = operatorPath,
                termsOperatorProfilePath = writeTermsOperatorProfile(),
                completenessChecker = completenessChecker(complete = true),
                canonicalJsonConverter = canonicalJsonConverter,
            )
        val exception =
            assertFailsWith<IllegalStateException> {
                service.privacyServiceCanonicalJson()
            }

        assertTrue(exception.message?.contains("missing or incomplete")!!)
    }

    @Test
    fun `fails when last full profile has no values`() {
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
            createLegalService(
                operatorProfilePath = operatorPath,
                termsOperatorProfilePath = writeTermsOperatorProfile(),
                completenessChecker = completenessChecker(complete = true),
                canonicalJsonConverter = canonicalJsonConverter,
            )
        val exception =
            assertFailsWith<IllegalStateException> {
                service.privacyServiceCanonicalJson()
            }

        assertTrue(exception.message?.contains("missing or incomplete")!!)
    }

    private fun createLegalService(
        operatorProfilePath: Path? = null,
        termsOperatorProfilePath: Path? = null,
        completenessChecker: ClearTermsProfileChainCompletenessChecker? = null,
    ) = straightway.mosa.legal.createLegalService(
        operatorProfilePath = operatorProfilePath ?: writeOperatorProfile(),
        termsOperatorProfilePath = termsOperatorProfilePath ?: writeTermsOperatorProfile(),
        completenessChecker = completenessChecker ?: completenessChecker(complete = true),
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

    private fun writeTermsOperatorProfile(): Path {
        val path = Files.createTempDirectory("legal-service").resolve("mosa_terms.yaml")
        Files.writeString(path, validTermsOperatorProfileYaml())
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

    private fun <T> captureStdout(
        stdout: ByteArrayOutputStream,
        block: () -> T,
    ): T {
        val previous = System.out
        System.setOut(PrintStream(stdout, true, Charsets.UTF_8))
        return try {
            block()
        } finally {
            System.setOut(previous)
        }
    }

    private fun captureLegalServiceLogs(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(LegalService::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        return try {
            block()
            appender.list.map { it.formattedMessage }
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun minimallyFilledTemplateYaml(templateYaml: String): String =
        templateYaml
            .lineSequence()
            .joinToString("\n") { line ->
                when {
                    line.contains(": null # type: string") -> {
                        line.replace(": null # type: string", """: "Filled value"""")
                    }

                    line.contains(": null # type: time") -> {
                        line.replace(": null # type: time", """: "2026-03-14T00:00:00Z"""")
                    }

                    line.contains(": null # type: duration") -> {
                        line.replace(": null # type: duration", """: "P30D"""")
                    }

                    line.contains(": null # type: weekday") -> {
                        line.replace(": null # type: weekday", """: "mon"""")
                    }

                    line.contains(": null # type: number") -> {
                        line.replace(": null # type: number", ": 1")
                    }

                    line.contains(": null # TODO:") -> {
                        line.replace(Regex(""": null # TODO:.*$"""), """: "Filled text"""")
                    }

                    else -> {
                        line
                    }
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
            AUTHORITY_SUPERVISORY_ADDRESS: "Example Authority Street 2, 12345 Example City"
            AUTHORITY_SUPERVISORY_NAME: "Example Supervisory Authority"
            AUTHORITY_SUPERVISORY_WEBSITE: "https://authority.example"
            ORG_CONTROLLER_NAME: "Operator Corp"
            ORG_CONTROLLER_ADDRESS: "Example Street 1, 12345 Example City"
            CONTACT_CONTROLLER_EMAIL: "legal@operator.example"
            CONTACT_DPO_OR_NOT_REQUIRED: "not required"
            CONTACT_PRIVACY_EMAIL: "privacy@operator.example"
            DOC_EFFECTIVE_DATE: "2026-03-13T00:00:00Z"
            RECIPIENTS_PROCESSORS_INFO: "No processors"
            SERVICE_DISPLAY_NAME: "Operator Service"
            URL_CONTROLLER_WEBSITE: "https://operator.example"
        """.trimIndent()

    private fun validTermsOperatorProfileYaml(): String =
        """
        kind: profile
        id: terms-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-terms-service
          version: "1.0.0-draft1"
        overrides:
          values:
            ORG_CONTROLLER_NAME: "Operator Corp"
            CONTACT_CONTROLLER_EMAIL: "legal@operator.example"
            DOC_EFFECTIVE_DATE: "2026-03-13T00:00:00Z"
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

    private fun operatorProfileYamlWithNullValue(): String =
        """
        kind: profile
        id: privacy-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-privacy-gdpr-service
          version: "1.0.0-draft2"
        overrides:
          values:
            ORG_CONTROLLER_NAME: null
            CONTACT_CONTROLLER_EMAIL: "legal@operator.example"
            DOC_EFFECTIVE_DATE: "2026-03-13T00:00:00Z"
            SERVICE_DISPLAY_NAME: "Operator Service"
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
            decimalSeparator = ",",
            groupingSeparator = ".",
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
