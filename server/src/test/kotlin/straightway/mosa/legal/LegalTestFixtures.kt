package straightway.mosa.legal

import straightway.mosa.legal.clearterms.canonical.CanonicalNodeRenderer
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker
import straightway.mosa.legal.clearterms.validation.ProfileValidator
import straightway.mosa.legal.clearterms.yaml.YamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.yaml.YamlLanguageFragmentMerger

internal fun createLegalProfileSource(
    resourceTextReader: ResourceTextReader =
        ResourceTextReader { path ->
            LegalProfileSource::class.java.classLoader
                .getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        },
    resourceDirectoryEntriesProvider: ResourceDirectoryEntriesProvider =
        ResourceDirectoryEntriesProvider { null },
    resourceUrlsProvider: ResourceUrlsProvider =
        ResourceUrlsProvider { path ->
            LegalProfileSource::class.java.classLoader.getResources(path)
        },
) = LegalProfileSource(
    canonicalJsonConverter = YamlCanonicalJsonConverter(),
    languageFragmentMerger = YamlLanguageFragmentMerger(),
    resourceTextReader = resourceTextReader,
    resourceDirectoryEntriesProvider = resourceDirectoryEntriesProvider,
    resourceUrlsProvider = resourceUrlsProvider,
)

internal fun createLegalChainValidator(
    completenessChecker: ProfileChainCompletenessChecker,
    profileSource: LegalProfileSource,
) = LegalChainValidator(
    completenessChecker = completenessChecker,
    parser = JsonParser(),
    reader = DocumentReader(),
    profileValidator = ProfileValidator(PlaceholderParser()),
    profileSource = profileSource,
)

internal fun createLegalDocumentFactory() =
    LegalDocumentFactory(
        parser = JsonParser(),
        reader = DocumentReader(),
        profileValidator = ProfileValidator(PlaceholderParser()),
        canonicalNodeRenderer = CanonicalNodeRenderer(),
    )

internal fun createLegalService(
    operatorProfilePath: java.nio.file.Path,
    termsOperatorProfilePath: java.nio.file.Path,
    completenessChecker: ProfileChainCompletenessChecker,
    canonicalJsonConverter: YamlCanonicalJsonConverter = YamlCanonicalJsonConverter(),
    resourceTextReader: ResourceTextReader =
        ResourceTextReader { path ->
            LegalService::class.java.classLoader
                .getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        },
    resourceDirectoryEntriesProvider: ResourceDirectoryEntriesProvider =
        ResourceDirectoryEntriesProvider { null },
    resourceUrlsProvider: ResourceUrlsProvider =
        ResourceUrlsProvider { path ->
            LegalService::class.java.classLoader.getResources(path)
        },
): LegalService {
    val profileSource =
        LegalProfileSource(
            canonicalJsonConverter = canonicalJsonConverter,
            languageFragmentMerger = YamlLanguageFragmentMerger(),
            resourceTextReader = resourceTextReader,
            resourceDirectoryEntriesProvider = resourceDirectoryEntriesProvider,
            resourceUrlsProvider = resourceUrlsProvider,
        )
    return LegalService(
        operatorProfilePath = operatorProfilePath,
        termsOperatorProfilePath = termsOperatorProfilePath,
        profileSource = profileSource,
        chainValidator =
            LegalChainValidator(
                completenessChecker = completenessChecker,
                parser = JsonParser(),
                reader = DocumentReader(),
                profileValidator = ProfileValidator(PlaceholderParser()),
                profileSource = profileSource,
            ),
        templateWriter = LegalOperatorProfileTemplateWriter(ProfileChainCompletenessChecker()),
        documentFactory = createLegalDocumentFactory(),
    )
}

internal fun operatorProfileYaml(): String =
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

internal fun operatorProfileYamlWithInvalidValueType(): String =
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

internal fun operatorProfileYamlWithNullValue(): String =
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

internal fun syntheticBaselineWithAllPlaceholderTypesAndUnresolvedEnglishText(): String =
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
      de:
        - id: unresolved-slot-de
          text: null
    """.trimIndent()

internal fun syntheticBaselineWithRequiredStringPlaceholder(): String =
    """
    kind: baseline
    id: synthetic-baseline
    version: "1.0.0"
    texts:
      en:
        - id: required
          text: "Required {{string:REQ_KEY}}"
    """.trimIndent()

internal fun syntheticProfileYaml(
    id: String,
    parentId: String,
) = """
    kind: profile
    id: $id
    version: "1.0.0"
    parent:
      id: $parentId
      version: "1.0.0"
    overrides: {}
    """.trimIndent()
