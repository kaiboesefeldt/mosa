package straightway.mosa.legal

import io.github.oshai.kotlinlogging.KotlinLogging
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode
import java.nio.file.Path

class LegalService internal constructor(
    private val operatorProfilePath: Path,
    private val termsOperatorProfilePath: Path,
    private val profileSource: LegalProfileSource,
    private val chainValidator: LegalChainValidator,
    private val templateWriter: LegalOperatorProfileTemplateWriter,
    private val documentFactory: LegalDocumentFactory,
    private val privacyResourceDirectory: String = LegalDocumentKind.Privacy.resourceDirectory,
    private val termsResourceDirectory: String = LegalDocumentKind.Terms.resourceDirectory,
) {
    private val privacyDocuments: LegalDocuments by lazy {
        loadDocuments(
            kind = LegalDocumentKind.Privacy,
            operatorProfilePath = operatorProfilePath,
        )
    }
    private val termsDocuments: LegalDocuments by lazy {
        loadDocuments(
            kind = LegalDocumentKind.Terms,
            operatorProfilePath = termsOperatorProfilePath,
        )
    }

    fun validateStartup() {
        val createdProfilePaths = ensureMissingOperatorProfilesExist()
        if (createdProfilePaths.isNotEmpty()) {
            logMissingOrIncompleteOperatorProfiles(
                reason = "Created missing operator profile template files.",
                affectedProfilePaths = createdProfilePaths,
            )
        }
        validateOperatorProfileForStartup(
            kind = LegalDocumentKind.Privacy,
            profilePath = operatorProfilePath,
        )
        validateOperatorProfileForStartup(
            kind = LegalDocumentKind.Terms,
            profilePath = termsOperatorProfilePath,
        )
        privacyServiceCanonicalJson()
        termsOperatorCanonicalJson()
    }

    fun privacyBaselineCanonicalJson(): String = privacyDocuments.baselineCanonicalJson

    fun privacyDefaultCanonicalJson(): String = privacyDocuments.requiredProfileCanonicalJson(LegalDocumentKind.Privacy.defaultProfileId)

    fun privacyMosaCanonicalJson(): String = privacyDocuments.requiredProfileCanonicalJson(LegalDocumentKind.Privacy.mosaProfileId)

    fun privacyServiceCanonicalJson(): String = privacyDocuments.lastProfileCanonicalJson

    fun privacyDocumentChainResponse(): LegalDocumentChainResponse = privacyDocuments.toDocumentChainResponse()

    fun termsBaselineCanonicalJson(): String = termsDocuments.baselineCanonicalJson

    fun termsDefaultCanonicalJson(): String = termsDocuments.requiredProfileCanonicalJson(LegalDocumentKind.Terms.defaultProfileId)

    fun termsMosaCanonicalJson(): String = termsDocuments.requiredProfileCanonicalJson(LegalDocumentKind.Terms.mosaProfileId)

    fun termsOperatorCanonicalJson(): String = termsDocuments.lastProfileCanonicalJson

    fun termsDocumentChainResponse(): LegalDocumentChainResponse = termsDocuments.toDocumentChainResponse()

    private fun loadDocuments(
        kind: LegalDocumentKind,
        operatorProfilePath: Path,
    ): LegalDocuments {
        val mandatoryContext = loadMandatoryChainContext(kind)
        val operatorYaml =
            profileSource.readOptionalProfile(operatorProfilePath) ?: run {
                templateWriter.createMissingOperatorProfileFile(
                    profilePath = operatorProfilePath,
                    mandatoryContext = mandatoryContext,
                    kind = kind,
                )
                failForMissingOrIncompleteOperatorProfile(
                    reason = "Operator profile file is missing at '${operatorProfilePath.toAbsolutePath().normalize()}'",
                    affectedProfilePath = operatorProfilePath,
                )
            }
        val fullConversion =
            try {
                profileSource.convertFullChain(
                    mandatoryContext = mandatoryContext,
                    operatorYaml = operatorYaml,
                )
            } catch (exception: ClearTermsValidationException) {
                return fallbackToOperatorProfile(
                    mandatoryConversion = mandatoryContext.conversion,
                    kind = kind,
                    reason =
                        "Operator profile at '${operatorProfilePath.toAbsolutePath().normalize()}' " +
                            "is invalid and could not be converted: ${exception.message}",
                    affectedProfilePath = operatorProfilePath,
                    operatorValuesOverride = documentFactory.extractOperatorValuesBestEffort(operatorYaml),
                )
            }
        val fullChainContext = chainValidator.createFullChainContext(fullConversion)
        try {
            chainValidator.requireValidFullChain(
                fullChainContext = fullChainContext,
                affectedProfilePath = operatorProfilePath,
            )
        } catch (exception: IllegalStateException) {
            failForMissingOrIncompleteOperatorProfile(
                reason = checkNotNull(exception.message),
                affectedProfilePath = operatorProfilePath,
            )
        }
        return documentFactory.createDocuments(fullConversion)
    }

    private fun validateOperatorProfileForStartup(
        kind: LegalDocumentKind,
        profilePath: Path,
    ) {
        chainValidator.validateOperatorProfileForStartup(
            kind = kind,
            profilePath = profilePath,
            mandatoryContext = loadMandatoryChainContext(kind),
        )
    }

    private fun loadMandatoryChainContext(kind: LegalDocumentKind): MandatoryChainContext =
        profileSource.loadMandatoryChainContext(
            kind = kind,
            resourceDirectory = resourceDirectoryFor(kind),
        )

    private fun resourceDirectoryFor(kind: LegalDocumentKind): String =
        when (kind) {
            LegalDocumentKind.Privacy -> privacyResourceDirectory
            LegalDocumentKind.Terms -> termsResourceDirectory
        }

    private fun ensureMissingOperatorProfilesExist(): List<Path> {
        val createdPaths = mutableListOf<Path>()
        createMissingOperatorProfileFileIfNeeded(
            kind = LegalDocumentKind.Privacy,
            profilePath = operatorProfilePath,
            createdPaths = createdPaths,
        )
        createMissingOperatorProfileFileIfNeeded(
            kind = LegalDocumentKind.Terms,
            profilePath = termsOperatorProfilePath,
            createdPaths = createdPaths,
        )
        return createdPaths
    }

    private fun createMissingOperatorProfileFileIfNeeded(
        kind: LegalDocumentKind,
        profilePath: Path,
        createdPaths: MutableList<Path>,
    ) {
        templateWriter.createMissingOperatorProfileFileIfNeeded(
            profilePath = profilePath,
            mandatoryContext = loadMandatoryChainContext(kind),
            kind = kind,
            createdPaths = createdPaths,
        )
    }

    private fun fallbackToOperatorProfile(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        kind: LegalDocumentKind,
        reason: String,
        affectedProfilePath: Path,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode>,
    ): LegalDocuments {
        logMissingOrIncompleteOperatorProfiles(
            reason = "$reason Falling back to mosa service profile.",
            affectedProfilePaths = listOf(affectedProfilePath),
        )
        return documentFactory.createFallbackDocuments(
            mandatoryConversion = mandatoryConversion,
            kind = kind,
            operatorValuesOverride = operatorValuesOverride,
        )
    }

    private fun logMissingOrIncompleteOperatorProfiles(
        reason: String,
        affectedProfilePaths: List<Path>,
    ) {
        val normalizedPaths = affectedProfilePaths.map { path -> path.toAbsolutePath().normalize() }
        logger.error {
            "Legal operator profile validation failed: $reason " +
                "Review and complete ${normalizedPaths.joinToString(", ") { "'$it'" }}."
        }
    }

    private fun failForMissingOrIncompleteOperatorProfile(
        reason: String,
        affectedProfilePath: Path,
    ): Nothing {
        logMissingOrIncompleteOperatorProfiles(
            reason = reason,
            affectedProfilePaths = listOf(affectedProfilePath),
        )
        throw IllegalStateException("Legal operator profile is missing or incomplete: $reason")
    }

    private companion object {
        private val logger = KotlinLogging.logger {}
    }
}
