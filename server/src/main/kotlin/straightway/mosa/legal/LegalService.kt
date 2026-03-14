package straightway.mosa.legal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import straightway.mosa.legal.clearterms.ClearTermsBaseline
import straightway.mosa.legal.clearterms.ClearTermsCanonicalJson
import straightway.mosa.legal.clearterms.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.ClearTermsJsonNode
import straightway.mosa.legal.clearterms.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.ClearTermsPlaceholderType
import straightway.mosa.legal.clearterms.ClearTermsProfile
import straightway.mosa.legal.clearterms.ClearTermsProfileChainCompletenessChecker
import straightway.mosa.legal.clearterms.ClearTermsProfileValidator
import straightway.mosa.legal.clearterms.ClearTermsTextBlock
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.ClearTermsYamlLanguageFragmentMerger
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path

class LegalService(
    private val canonicalJsonConverter: ClearTermsYamlCanonicalJsonConverter,
    private val operatorProfilePath: Path,
    private val termsOperatorProfilePath: Path,
    private val completenessChecker: ClearTermsProfileChainCompletenessChecker,
    private val parser: ClearTermsJsonParser,
    private val reader: ClearTermsDocumentReader,
    private val profileValidator: ClearTermsProfileValidator,
    private val canonicalJson: ClearTermsCanonicalJson,
    private val languageFragmentMerger: ClearTermsYamlLanguageFragmentMerger = ClearTermsYamlLanguageFragmentMerger(),
    private val templateCompletenessChecker: ClearTermsProfileChainCompletenessChecker = ClearTermsProfileChainCompletenessChecker(),
) {
    private val documents: PrivacyLegalDocuments by lazy { loadPrivacyDocuments() }
    private val termsDocuments: TermsLegalDocuments by lazy { loadTermsDocuments() }

    fun validateStartup() {
        val createdProfilePaths = ensureMissingOperatorProfilesExist()
        if (createdProfilePaths.isNotEmpty()) {
            logMissingOrIncompleteOperatorProfiles(
                reason = "Created missing operator profile template files.",
                affectedProfilePaths = createdProfilePaths,
            )
        }
        validateOperatorProfileForStartup(
            profilePath = operatorProfilePath,
            mandatoryContext = loadMandatoryChainContext(),
        )
        validateOperatorProfileForStartup(
            profilePath = termsOperatorProfilePath,
            mandatoryContext =
                loadMandatoryChainContext(
                    baselineResource = TERMS_BASELINE_RESOURCE,
                    defaultResource = TERMS_DEFAULT_RESOURCE,
                    mosaResource = TERMS_SERVICE_RESOURCE,
                ),
        )
        privacyServiceCanonicalJson()
        termsOperatorCanonicalJson()
    }

    fun privacyBaselineCanonicalJson(): String = documents.baselineCanonicalJson

    fun privacyDefaultCanonicalJson(): String = documents.defaultCanonicalJson

    fun privacyMosaCanonicalJson(): String = documents.mosaCanonicalJson

    fun privacyServiceCanonicalJson(): String = documents.serviceCanonicalJson

    fun termsBaselineCanonicalJson(): String = termsDocuments.baselineCanonicalJson

    fun termsDefaultCanonicalJson(): String = termsDocuments.defaultCanonicalJson

    fun termsMosaCanonicalJson(): String = termsDocuments.mosaCanonicalJson

    fun termsOperatorCanonicalJson(): String = termsDocuments.operatorCanonicalJson

    fun termsServiceCanonicalJson(): String = termsOperatorCanonicalJson()

    private fun loadPrivacyDocuments(): PrivacyLegalDocuments {
        val mandatoryContext = loadMandatoryChainContext()
        val operatorYaml =
            readOptionalProfile(operatorProfilePath) ?: run {
                createMissingOperatorProfileFile(
                    profilePath = operatorProfilePath,
                    mandatoryContext = mandatoryContext,
                    fallbackProfileId = "mosa-privacy-fallback-service",
                )
                failForMissingOrIncompleteOperatorProfile(
                    mandatoryConversion = mandatoryContext.conversion,
                    mandatoryCanonicals = mandatoryContext.profileCanonicalJsons,
                    reason = "Operator profile file is missing at '${
                        operatorProfilePath.toAbsolutePath().normalize()
                    }'",
                    affectedProfilePath = operatorProfilePath,
                    fallbackProfileId = "mosa-privacy-fallback-service",
                )
            }
        return loadPrivacyDocumentsFromOperatorProfile(
            mandatoryContext = mandatoryContext,
            operatorYaml = operatorYaml,
        )
    }

    private fun loadTermsDocuments(): TermsLegalDocuments {
        val mandatoryContext =
            loadMandatoryChainContext(
                baselineResource = TERMS_BASELINE_RESOURCE,
                defaultResource = TERMS_DEFAULT_RESOURCE,
                mosaResource = TERMS_SERVICE_RESOURCE,
            )
        val operatorYaml =
            readOptionalProfile(termsOperatorProfilePath) ?: run {
                createMissingOperatorProfileFile(
                    profilePath = termsOperatorProfilePath,
                    mandatoryContext = mandatoryContext,
                    fallbackProfileId = "mosa-terms-fallback-operator",
                )
                failForMissingOrIncompleteOperatorProfile(
                    mandatoryConversion = mandatoryContext.conversion,
                    mandatoryCanonicals = mandatoryContext.profileCanonicalJsons,
                    reason = "Operator profile file is missing at '${termsOperatorProfilePath.toAbsolutePath().normalize()}'",
                    affectedProfilePath = termsOperatorProfilePath,
                    fallbackProfileId = "mosa-terms-fallback-operator",
                )
            }
        val fullConversion =
            try {
                convertFullChain(
                    mandatoryContext = mandatoryContext,
                    operatorYaml = operatorYaml,
                )
            } catch (exception: ClearTermsValidationException) {
                return fallbackToTermsOperatorProfile(
                    mandatoryConversion = mandatoryContext.conversion,
                    mandatoryCanonicals = mandatoryContext.profileCanonicalJsons,
                    reason =
                        "Operator profile at '${termsOperatorProfilePath.toAbsolutePath().normalize()}' " +
                            "is invalid and could not be converted: ${exception.message}",
                    affectedProfilePath = termsOperatorProfilePath,
                    operatorValuesOverride = extractOperatorValuesBestEffort(operatorYaml),
                )
            }
        val fullChainContext = createFullChainContext(fullConversion)
        val fallbackDocuments =
            fallbackForInvalidTermsFullChain(
                mandatoryContext = mandatoryContext,
                fullChainContext = fullChainContext,
            )
        if (fallbackDocuments != null) {
            return fallbackDocuments
        }
        return createTermsLegalDocuments(fullConversion)
    }

    private fun loadPrivacyDocumentsFromOperatorProfile(
        mandatoryContext: MandatoryChainContext,
        operatorYaml: String,
    ): PrivacyLegalDocuments {
        val fullConversion =
            try {
                convertFullChain(
                    mandatoryContext = mandatoryContext,
                    operatorYaml = operatorYaml,
                )
            } catch (exception: ClearTermsValidationException) {
                return fallbackToMosaServiceProfile(
                    mandatoryConversion = mandatoryContext.conversion,
                    mandatoryCanonicals = mandatoryContext.profileCanonicalJsons,
                    reason =
                        "Operator profile at '${operatorProfilePath.toAbsolutePath().normalize()}' " +
                            "is invalid and could not be converted: ${exception.message}",
                    affectedProfilePath = operatorProfilePath,
                    operatorValuesOverride = extractOperatorValuesBestEffort(operatorYaml),
                )
            }
        val fullChainContext = createFullChainContext(fullConversion)
        val fallbackDocuments =
            fallbackForInvalidFullChain(
                mandatoryContext = mandatoryContext,
                fullChainContext = fullChainContext,
            )
        if (fallbackDocuments != null) {
            return fallbackDocuments
        }
        return createPrivacyLegalDocuments(fullConversion)
    }

    private fun loadMandatoryChainContext(): MandatoryChainContext =
        loadMandatoryChainContext(
            baselineResource = BASELINE_RESOURCE,
            defaultResource = DEFAULT_RESOURCE,
            mosaResource = MOSA_RESOURCE,
        )

    private fun loadMandatoryChainContext(
        baselineResource: String,
        defaultResource: String,
        mosaResource: String,
    ): MandatoryChainContext {
        val baselineYaml = readRequiredResource(baselineResource)
        val defaultYaml = readRequiredResource(defaultResource)
        val mosaYaml = readRequiredResource(mosaResource)
        val mandatoryConversion =
            canonicalJsonConverter.convert(
                baselineYaml,
                listOf(defaultYaml, mosaYaml),
            )
        return MandatoryChainContext(
            baselineYaml = baselineYaml,
            defaultYaml = defaultYaml,
            mosaYaml = mosaYaml,
            conversion = mandatoryConversion,
            profileCanonicalJsons = mandatoryConversion.profiles.map { artifact -> artifact.canonicalJson },
        )
    }

    private fun convertFullChain(
        mandatoryContext: MandatoryChainContext,
        operatorYaml: String,
    ): ClearTermsYamlCanonicalJsonConverter.ConversionResult =
        canonicalJsonConverter.convert(
            mandatoryContext.baselineYaml,
            listOf(
                mandatoryContext.defaultYaml,
                mandatoryContext.mosaYaml,
                operatorYaml,
            ),
        )

    private fun validateOperatorProfileForStartup(
        profilePath: Path,
        mandatoryContext: MandatoryChainContext,
    ) {
        val normalizedPath = profilePath.toAbsolutePath().normalize()
        val operatorYaml =
            readOptionalProfile(profilePath)
                ?: throw IllegalStateException(
                    "Legal operator profile is missing or incomplete: " +
                        "Operator profile file is missing at '$normalizedPath'",
                )
        val fullConversion =
            try {
                convertFullChain(
                    mandatoryContext = mandatoryContext,
                    operatorYaml = operatorYaml,
                )
            } catch (exception: ClearTermsValidationException) {
                throw IllegalStateException(
                    "Legal operator profile is missing or incomplete: " +
                        "Operator profile at '$normalizedPath' is invalid and could not be converted: ${exception.message}",
                )
            }
        val fullChainContext = createFullChainContext(fullConversion)
        val analysis =
            analyzeChainCompleteness(
                baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
                profileCanonicalJsons = fullChainContext.profileCanonicalJsons,
            )
        if (!analysis.complete) {
            val incompleteDetails = incompleteChainDetails(analysis)
            throw IllegalStateException(
                "Legal operator profile is missing or incomplete: " +
                    "Legal profile chain is incomplete for '$normalizedPath'$incompleteDetails",
            )
        }
        val valueTypeValidationError =
            validateChainValues(
                baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
                profiles = fullChainContext.profiles,
                requireAll = false,
            )
        if (valueTypeValidationError != null) {
            throw IllegalStateException(
                "Legal operator profile is missing or incomplete: " +
                    "Legal profile chain has invalid placeholder values in '$normalizedPath': ${valueTypeValidationError.message}",
            )
        }
        val missingRequiredValuesError = missingRequiredChainValuesError(fullChainContext)
        if (missingRequiredValuesError != null) {
            throw IllegalStateException(
                "Legal operator profile is missing or incomplete: " +
                    "Legal profile chain has invalid placeholder values in '$normalizedPath': ${missingRequiredValuesError.message}",
            )
        }
    }

    private fun incompleteChainDetails(analysis: ClearTermsProfileChainCompletenessChecker.Result): String {
        val details = mutableListOf<String>()
        if (analysis.missingPlaceholderKeys.isNotEmpty()) {
            details += "missing placeholder keys: ${analysis.missingPlaceholderKeys.joinToString(", ")}"
        }
        val unresolvedBlocks =
            analysis.unresolvedRequiredBlockIdsByLanguage
                .flatMap { (language, blockIds) -> blockIds.map { blockId -> "$language:$blockId" } }
        if (unresolvedBlocks.isNotEmpty()) {
            details += "unresolved block ids: ${unresolvedBlocks.joinToString(", ")}"
        }
        return if (details.isEmpty()) "" else " (${details.joinToString("; ")})"
    }

    private fun fallbackForInvalidFullChain(
        mandatoryContext: MandatoryChainContext,
        fullChainContext: FullChainContext,
    ): PrivacyLegalDocuments? {
        val analysis = analyzeChainCompleteness(fullChainContext.conversion.baseline.canonicalJson, fullChainContext.profileCanonicalJsons)
        if (!analysis.complete) {
            failForMissingOrIncompleteOperatorProfile(
                mandatoryConversion = mandatoryContext.conversion,
                mandatoryCanonicals = mandatoryContext.profileCanonicalJsons,
                reason = "Legal profile chain is incomplete for '${operatorProfilePath.toAbsolutePath().normalize()}'",
                affectedProfilePath = operatorProfilePath,
                skeletonYamlOverride = analysis.skeletonYaml,
                fallbackProfileId = "mosa-privacy-fallback-service",
            )
        }
        throwWhenChainHasInvalidValueTypes(fullChainContext)
        val fullChainValidationError = missingRequiredChainValuesError(fullChainContext)
        if (fullChainValidationError == null) {
            return null
        }
        failForMissingOrIncompleteOperatorProfile(
            mandatoryConversion = mandatoryContext.conversion,
            mandatoryCanonicals = mandatoryContext.profileCanonicalJsons,
            reason =
                "Legal profile chain has invalid placeholder values in " +
                    "'${operatorProfilePath.toAbsolutePath().normalize()}': ${fullChainValidationError.message}",
            affectedProfilePath = operatorProfilePath,
            operatorValuesOverride = serviceProfileValues(fullChainContext.profiles),
            fallbackProfileId = "mosa-privacy-fallback-service",
        )
    }

    private fun fallbackForInvalidTermsFullChain(
        mandatoryContext: MandatoryChainContext,
        fullChainContext: FullChainContext,
    ): TermsLegalDocuments? {
        val analysis = analyzeChainCompleteness(fullChainContext.conversion.baseline.canonicalJson, fullChainContext.profileCanonicalJsons)
        if (!analysis.complete) {
            failForMissingOrIncompleteOperatorProfile(
                mandatoryConversion = mandatoryContext.conversion,
                mandatoryCanonicals = mandatoryContext.profileCanonicalJsons,
                reason = "Legal profile chain is incomplete for '${termsOperatorProfilePath.toAbsolutePath().normalize()}'",
                affectedProfilePath = termsOperatorProfilePath,
                skeletonYamlOverride = analysis.skeletonYaml,
                fallbackProfileId = "mosa-terms-fallback-operator",
            )
        }
        throwWhenChainHasInvalidValueTypes(fullChainContext)
        val fullChainValidationError = missingRequiredChainValuesError(fullChainContext)
        if (fullChainValidationError == null) {
            return null
        }
        failForMissingOrIncompleteOperatorProfile(
            mandatoryConversion = mandatoryContext.conversion,
            mandatoryCanonicals = mandatoryContext.profileCanonicalJsons,
            reason =
                "Legal profile chain has invalid placeholder values in " +
                    "'${termsOperatorProfilePath.toAbsolutePath().normalize()}': ${fullChainValidationError.message}",
            affectedProfilePath = termsOperatorProfilePath,
            operatorValuesOverride = serviceProfileValues(fullChainContext.profiles),
            fallbackProfileId = "mosa-terms-fallback-operator",
        )
    }

    private fun throwWhenChainHasInvalidValueTypes(fullChainContext: FullChainContext) {
        val valueTypeValidationError =
            validateChainValues(
                baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
                profiles = fullChainContext.profiles,
                requireAll = false,
            )
        if (valueTypeValidationError != null) {
            throw IllegalStateException(
                "Legal profile chain has invalid placeholder values: ${valueTypeValidationError.message}",
            )
        }
    }

    private fun missingRequiredChainValuesError(fullChainContext: FullChainContext): ClearTermsValidationException? =
        validateChainValues(
            baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
            profiles = fullChainContext.profiles,
            requireAll = true,
        )

    private fun analyzeChainCompleteness(
        baselineCanonicalJson: String,
        profileCanonicalJsons: List<String>,
    ): ClearTermsProfileChainCompletenessChecker.Result =
        completenessChecker.analyze(
            baselineCanonicalJson = baselineCanonicalJson,
            profileChainCanonicalJsons = profileCanonicalJsons,
        )

    private fun createFullChainContext(conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult): FullChainContext =
        FullChainContext(
            conversion = conversion,
            profiles = conversion.profiles.map { artifact -> reader.readProfile(parser.parse(artifact.canonicalJson)) },
            profileCanonicalJsons = conversion.profiles.map { artifact -> artifact.canonicalJson },
        )

    private fun serviceProfileValues(profiles: List<ClearTermsProfile>): Map<String, ClearTermsJsonNode.ScalarNode> =
        profiles.lastOrNull()?.overrides?.values ?: emptyMap()

    private fun readRequiredResource(path: String): String {
        val baseYaml = readResource(path) ?: throw IllegalStateException("Missing resource '$path'")
        val fragmentYamls = listResourceFragments(path).map { fragmentPath -> readRequiredResourceWithoutFragments(fragmentPath) }
        return languageFragmentMerger.merge(baseYaml, fragmentYamls)
    }

    private fun readOptionalProfile(path: Path): String? {
        val normalizedPath = path.toAbsolutePath().normalize()
        if (!Files.exists(normalizedPath)) {
            return null
        }
        val baseYaml = Files.readString(normalizedPath, Charsets.UTF_8)
        val fragmentYamls = listSiblingFileFragments(normalizedPath).map { fragmentPath -> Files.readString(fragmentPath, Charsets.UTF_8) }
        return languageFragmentMerger.merge(baseYaml, fragmentYamls)
    }

    private fun readRequiredResourceWithoutFragments(path: String): String =
        readResource(path) ?: throw IllegalStateException("Missing resource '$path'")

    private fun readResource(path: String): String? =
        javaClass.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }

    private fun listSiblingFileFragments(basePath: Path): List<Path> {
        val baseFileName = basePath.fileName.toString()
        if (!baseFileName.endsWith(".yaml")) {
            return emptyList()
        }
        val prefix = "${baseFileName.removeSuffix(".yaml")}."
        val parent = basePath.parent ?: Path.of(".")
        return Files
            .list(parent)
            .use { stream ->
                stream
                    .filter { candidate ->
                        Files.isRegularFile(candidate) &&
                            candidate.fileName.toString() != baseFileName &&
                            candidate.fileName.toString().startsWith(prefix) &&
                            candidate.fileName.toString().endsWith(".yaml")
                    }.sorted()
                    .toList()
            }
    }

    private fun listResourceFragments(basePath: String): List<String> {
        val baseFileName = basePath.substringAfterLast('/')
        if (!baseFileName.endsWith(".yaml")) {
            return emptyList()
        }
        val prefix = "${baseFileName.removeSuffix(".yaml")}."
        val directoryPath = basePath.substringBeforeLast('/', "")
        val discovered = linkedSetOf<String>()
        val resources = javaClass.classLoader.getResources(directoryPath)
        while (resources.hasMoreElements()) {
            val directoryUrl = resources.nextElement()
            when (directoryUrl.protocol) {
                "file" -> {
                    val directory = Path.of(directoryUrl.toURI())
                    Files
                        .list(directory)
                        .use { stream ->
                            stream
                                .filter { candidate ->
                                    Files.isRegularFile(candidate) &&
                                        candidate.fileName.toString() != baseFileName &&
                                        candidate.fileName.toString().startsWith(prefix) &&
                                        candidate.fileName.toString().endsWith(".yaml")
                                }.map { candidate ->
                                    if (directoryPath.isEmpty()) {
                                        candidate.fileName.toString()
                                    } else {
                                        "$directoryPath/${candidate.fileName}"
                                    }
                                }.sorted()
                                .forEach { candidatePath -> discovered.add(candidatePath) }
                        }
                }
                "jar" -> {
                    val connection = directoryUrl.openConnection() as JarURLConnection
                    val directoryEntryPrefix = connection.entryName.trimEnd('/') + "/"
                    val siblingNames =
                        connection
                            .jarFile
                            .entries()
                            .asSequence()
                            .map { entry -> entry.name }
                            .filter { entryName ->
                                entryName.startsWith(directoryEntryPrefix) &&
                                    !entryName.removePrefix(directoryEntryPrefix).contains("/")
                            }.map { entryName -> entryName.removePrefix(directoryEntryPrefix) }
                            .filter { name ->
                                name != baseFileName &&
                                    name.startsWith(prefix) &&
                                    name.endsWith(".yaml")
                            }.sorted()
                    siblingNames.forEach { name ->
                        if (directoryPath.isEmpty()) {
                            discovered.add(name)
                        } else {
                            discovered.add("$directoryPath/$name")
                        }
                    }
                }
            }
        }
        return discovered.toList()
    }

    private fun ensureMissingOperatorProfilesExist(): List<Path> {
        val createdPaths = mutableListOf<Path>()
        createMissingOperatorProfileFileIfNeeded(
            profilePath = operatorProfilePath,
            mandatoryContext = loadMandatoryChainContext(),
            templateMetadata = templateMetadataFor(PRIVACY_FALLBACK_PROFILE_ID),
            createdPaths = createdPaths,
        )
        createMissingOperatorProfileFileIfNeeded(
            profilePath = termsOperatorProfilePath,
            mandatoryContext =
                loadMandatoryChainContext(
                    baselineResource = TERMS_BASELINE_RESOURCE,
                    defaultResource = TERMS_DEFAULT_RESOURCE,
                    mosaResource = TERMS_SERVICE_RESOURCE,
                ),
            templateMetadata = templateMetadataFor(TERMS_FALLBACK_PROFILE_ID),
            createdPaths = createdPaths,
        )
        return createdPaths
    }

    private fun createMissingOperatorProfileFile(
        profilePath: Path,
        mandatoryContext: MandatoryChainContext,
        fallbackProfileId: String,
    ) {
        createMissingOperatorProfileFileIfNeeded(
            profilePath = profilePath,
            mandatoryContext = mandatoryContext,
            templateMetadata = templateMetadataFor(fallbackProfileId),
            createdPaths = mutableListOf(),
        )
    }

    private fun createMissingOperatorProfileFileIfNeeded(
        profilePath: Path,
        mandatoryContext: MandatoryChainContext,
        templateMetadata: OperatorProfileTemplateMetadata,
        createdPaths: MutableList<Path>,
    ) {
        val normalizedPath = profilePath.toAbsolutePath().normalize()
        if (Files.exists(normalizedPath)) {
            return
        }
        Files.createDirectories(normalizedPath.parent ?: Path.of("."))
        Files.writeString(
            normalizedPath,
            renderOperatorProfileTemplate(
                mandatoryContext = mandatoryContext,
                templateMetadata = templateMetadata,
            ) + "\n",
            Charsets.UTF_8,
        )
        createdPaths.add(normalizedPath)
    }

    private fun renderOperatorProfileTemplate(
        mandatoryContext: MandatoryChainContext,
        templateMetadata: OperatorProfileTemplateMetadata,
    ): String {
        val analysis =
            templateCompletenessChecker.analyze(
                baselineCanonicalJson = mandatoryContext.conversion.baseline.canonicalJson,
                profileChainCanonicalJsons = mandatoryContext.profileCanonicalJsons,
            )
        val overridesYaml =
            analysis.skeletonYaml
                .lineSequence()
                .dropWhile { line -> line != "overrides:" }
                .joinToString("\n")
        return buildString {
            appendLine("kind: profile")
            appendLine("""id: "${templateMetadata.profileId}"""")
            appendLine("""version: "${templateMetadata.profileVersion}"""")
            appendLine("parent:")
            appendLine("""  id: "${templateMetadata.parentId}"""")
            appendLine("""  version: "${templateMetadata.parentVersion}"""")
            append(overridesYaml)
        }
    }

    private fun templateMetadataFor(fallbackProfileId: String): OperatorProfileTemplateMetadata =
        when (fallbackProfileId) {
            PRIVACY_FALLBACK_PROFILE_ID ->
                OperatorProfileTemplateMetadata(
                    profileId = PRIVACY_OPERATOR_PROFILE_ID,
                    profileVersion = PRIVACY_OPERATOR_PROFILE_VERSION,
                    parentId = "mosa-privacy-gdpr-service",
                    parentVersion = "1.0.0-draft2",
                )
            TERMS_FALLBACK_PROFILE_ID ->
                OperatorProfileTemplateMetadata(
                    profileId = TERMS_OPERATOR_PROFILE_ID,
                    profileVersion = TERMS_OPERATOR_PROFILE_VERSION,
                    parentId = "mosa-terms-service",
                    parentVersion = "1.0.0-draft1",
                )
            else -> throw IllegalArgumentException("Unsupported fallback profile id '$fallbackProfileId'")
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
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        mandatoryCanonicals: List<String>,
        reason: String,
        affectedProfilePath: Path,
        fallbackProfileId: String,
        skeletonYamlOverride: String? = null,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode> = emptyMap(),
    ): Nothing {
        val analysis =
            completenessChecker.analyze(
                baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
                profileChainCanonicalJsons = mandatoryCanonicals,
            )
        val exampleProfileCanonicalJson =
            synthesizedFallbackProfileCanonicalJson(
                mandatoryConversion = mandatoryConversion,
                operatorValuesOverride = operatorValuesOverride,
                fallbackProfileId = fallbackProfileId,
            )
        logMissingOrIncompleteOperatorProfiles(
            reason = reason,
            affectedProfilePaths = listOf(affectedProfilePath),
        )
        throw IllegalStateException("Legal operator profile is missing or incomplete: $reason")
    }

    private fun fallbackToMosaServiceProfile(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        mandatoryCanonicals: List<String>,
        reason: String,
        affectedProfilePath: Path,
        skeletonYamlOverride: String? = null,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode> = emptyMap(),
    ): PrivacyLegalDocuments {
        logMissingOrIncompleteOperatorProfiles(
            reason = "$reason Falling back to mosa service profile.",
            affectedProfilePaths = listOf(affectedProfilePath),
        )
        val fallbackServiceCanonicalJson =
            synthesizedFallbackProfileCanonicalJson(
                mandatoryConversion = mandatoryConversion,
                operatorValuesOverride = operatorValuesOverride,
                fallbackProfileId = "mosa-privacy-fallback-service",
            )
        return PrivacyLegalDocuments(
            baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
            defaultCanonicalJson = mandatoryConversion.profiles[0].canonicalJson,
            mosaCanonicalJson = mandatoryConversion.profiles[1].canonicalJson,
            serviceCanonicalJson = fallbackServiceCanonicalJson,
        )
    }

    private fun fallbackToTermsOperatorProfile(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        mandatoryCanonicals: List<String>,
        reason: String,
        affectedProfilePath: Path,
        skeletonYamlOverride: String? = null,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode> = emptyMap(),
    ): TermsLegalDocuments {
        logMissingOrIncompleteOperatorProfiles(
            reason = "$reason Falling back to mosa service profile.",
            affectedProfilePaths = listOf(affectedProfilePath),
        )
        val fallbackOperatorCanonicalJson =
            synthesizedFallbackProfileCanonicalJson(
                mandatoryConversion = mandatoryConversion,
                operatorValuesOverride = operatorValuesOverride,
                fallbackProfileId = "mosa-terms-fallback-operator",
            )
        return TermsLegalDocuments(
            baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
            defaultCanonicalJson = mandatoryConversion.profiles[0].canonicalJson,
            mosaCanonicalJson = mandatoryConversion.profiles[1].canonicalJson,
            operatorCanonicalJson = fallbackOperatorCanonicalJson,
        )
    }

    private fun synthesizedFallbackProfileCanonicalJson(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode>,
        fallbackProfileId: String,
    ): String {
        val baseline = parseBaselineFrom(mandatoryConversion.baseline.canonicalJson)
        val mandatoryProfiles = mandatoryConversion.profiles.map { artifact -> reader.readProfile(parser.parse(artifact.canonicalJson)) }
        val (effectiveTexts, mergedValues) = baseline.mergeWithProfiles(mandatoryProfiles)
        val fallbackValues =
            fallbackValues(
                effectiveTexts = effectiveTexts,
                mergedValues = mergedValues,
                operatorValuesOverride = operatorValuesOverride,
            )
        val textOverrides = textOverridesForUnresolvedBlocks(effectiveTexts)
        val profileNode =
            fallbackServiceProfileNode(
                parentProfile = mandatoryProfiles.last(),
                parentHash = mandatoryConversion.profiles.last().hash,
                fallbackValues = fallbackValues,
                textOverrides = textOverrides,
                fallbackProfileId = fallbackProfileId,
            )
        return canonicalJson.canonicalize(profileNode)
    }

    private fun fallbackValues(
        effectiveTexts: Map<String, List<ClearTermsTextBlock>>,
        mergedValues: Map<String, ClearTermsJsonNode.ScalarNode>,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode>,
    ): Map<String, ClearTermsJsonNode.ScalarNode> {
        val mergedWithOperatorOverrides = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        mergedWithOperatorOverrides.putAll(mergedValues)
        mergedWithOperatorOverrides.putAll(operatorValuesOverride)
        val placeholderTypes = profileValidator.placeholdersByKey(effectiveTexts)
        val fallbackValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        placeholderTypes.forEach { (key, type) ->
            val existing = mergedWithOperatorOverrides[key]
            if (existing == null || existing is ClearTermsJsonNode.ScalarNode.NullNode) {
                fallbackValues[key] = defaultValueNode(type)
            } else if (operatorValuesOverride.containsKey(key)) {
                val coerced = coerceSingleValueOrNull(key, type, existing)
                if (coerced != null) {
                    fallbackValues[key] = coerced
                }
            }
        }
        return fallbackValues
    }

    private fun textOverridesForUnresolvedBlocks(effectiveTexts: Map<String, List<ClearTermsTextBlock>>): ClearTermsJsonNode.ObjectNode? {
        val unresolvedByLanguage = unresolvedBlockIdsByLanguage(effectiveTexts)
        if (unresolvedByLanguage.isEmpty()) {
            return null
        }
        return ClearTermsJsonNode.ObjectNode(
            unresolvedByLanguage.mapValues { (language, blockIds) ->
                val replace =
                    ClearTermsJsonNode.ObjectNode(
                        blockIds.associateWith { blockId ->
                            ClearTermsJsonNode.ScalarNode.StringNode(defaultReplacementText(language, blockId))
                        },
                    )
                ClearTermsJsonNode.ObjectNode(
                    mapOf(
                        "replace" to replace,
                    ),
                )
            },
        )
    }

    private fun unresolvedBlockIdsByLanguage(effectiveTexts: Map<String, List<ClearTermsTextBlock>>): Map<String, List<String>> =
        effectiveTexts
            .mapValues { (_, blocks) ->
                blocks.filter { block -> block.text == null }.map { block -> block.id }
            }.filterValues { ids -> ids.isNotEmpty() }

    private fun fallbackServiceProfileNode(
        parentProfile: ClearTermsProfile,
        parentHash: String,
        fallbackValues: Map<String, ClearTermsJsonNode.ScalarNode>,
        textOverrides: ClearTermsJsonNode.ObjectNode?,
        fallbackProfileId: String,
    ): ClearTermsJsonNode.ObjectNode {
        val overridesEntries =
            linkedMapOf<String, ClearTermsJsonNode>().apply {
                this["values"] = ClearTermsJsonNode.ObjectNode(fallbackValues)
                if (textOverrides != null) {
                    this["texts"] = textOverrides
                }
            }
        return ClearTermsJsonNode.ObjectNode(
            linkedMapOf(
                "kind" to ClearTermsJsonNode.ScalarNode.StringNode("profile"),
                "id" to ClearTermsJsonNode.ScalarNode.StringNode(fallbackProfileId),
                "version" to ClearTermsJsonNode.ScalarNode.StringNode("${parentProfile.version}-fallback"),
                "parent" to
                    ClearTermsJsonNode.ObjectNode(
                        linkedMapOf(
                            "id" to ClearTermsJsonNode.ScalarNode.StringNode(parentProfile.id),
                            "version" to ClearTermsJsonNode.ScalarNode.StringNode(parentProfile.version),
                            "hash" to ClearTermsJsonNode.ScalarNode.StringNode(parentHash),
                        ),
                    ),
                "overrides" to ClearTermsJsonNode.ObjectNode(overridesEntries),
            ),
        )
    }

    private fun validateChainValues(
        baselineCanonicalJson: String,
        profiles: List<ClearTermsProfile>,
        requireAll: Boolean,
    ): ClearTermsValidationException? =
        try {
            val baseline = parseBaselineFrom(baselineCanonicalJson)
            val (effectiveTexts, mergedValues) = baseline.mergeWithProfiles(profiles)
            profileValidator.validateFinalTextsResolved(effectiveTexts)
            val placeholderTypes = profileValidator.placeholdersByKey(effectiveTexts)
            profileValidator.validateAndCoerceValues(
                rawValues = mergedValues,
                types = placeholderTypes,
                requireAll = requireAll,
            )
            null
        } catch (exception: ClearTermsValidationException) {
            exception
        }

    private fun parseBaselineFrom(baselineCanonicalJson: String): ClearTermsBaseline =
        reader.readBaseline(parser.parse(baselineCanonicalJson))

    private data class MergeResult(
        val effectiveTexts: Map<String, List<ClearTermsTextBlock>>,
        val mergedValues: Map<String, ClearTermsJsonNode.ScalarNode>,
    )

    private fun ClearTermsBaseline.mergeWithProfiles(profiles: List<ClearTermsProfile>): MergeResult {
        var effectiveTexts = textsByLanguage
        val mergedValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        profiles.forEach { profile ->
            mergedValues.putAll(profile.overrides.values ?: emptyMap())
            effectiveTexts =
                profileValidator.applyTextOverrides(effectiveTexts, profile.overrides.texts)
        }
        return MergeResult(effectiveTexts, mergedValues)
    }

    private fun defaultValueNode(type: ClearTermsPlaceholderType): ClearTermsJsonNode.ScalarNode =
        when (type) {
            ClearTermsPlaceholderType.STRING -> ClearTermsJsonNode.ScalarNode.StringNode("not specified")
            ClearTermsPlaceholderType.NUMBER -> ClearTermsJsonNode.ScalarNode.NumberNode(0.0)
            ClearTermsPlaceholderType.DURATION -> ClearTermsJsonNode.ScalarNode.StringNode("infinite")
            ClearTermsPlaceholderType.TIME -> ClearTermsJsonNode.ScalarNode.StringNode("*-*-*")
            ClearTermsPlaceholderType.WEEKDAY -> ClearTermsJsonNode.ScalarNode.StringNode("mon")
        }

    private fun defaultReplacementText(
        language: String,
        blockId: String,
    ): String = if (language.lowercase().startsWith("de")) "Nicht angegeben ($blockId)." else "Not specified ($blockId)."

    private fun coerceSingleValueOrNull(
        key: String,
        type: ClearTermsPlaceholderType,
        value: ClearTermsJsonNode.ScalarNode,
    ): ClearTermsJsonNode.ScalarNode? =
        try {
            profileValidator.validateAndCoerceValues(
                rawValues = mapOf(key to value),
                types = mapOf(key to type),
                requireAll = true,
            )
            value
        } catch (_: ClearTermsValidationException) {
            null
        }

    private fun extractOperatorValuesBestEffort(operatorYaml: String): Map<String, ClearTermsJsonNode.ScalarNode> {
        val root =
            try {
                yamlMapper.readTree(operatorYaml)
            } catch (_: Exception) {
                return emptyMap()
            }
        val valuesNode = root.path("overrides").path("values")
        if (!valuesNode.isObject) {
            return emptyMap()
        }
        val values = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        valuesNode.properties().forEach { (key, node) ->
            when {
                node.isTextual -> values[key] = ClearTermsJsonNode.ScalarNode.StringNode(node.asText())
                node.isNumber -> values[key] = ClearTermsJsonNode.ScalarNode.NumberNode(node.asDouble())
                node.isNull -> values[key] = ClearTermsJsonNode.ScalarNode.NullNode
            }
        }
        return values
    }

    private fun renderYaml(canonicalJson: String): String = yamlMapper.writeValueAsString(yamlMapper.readTree(canonicalJson)).trimEnd()

    private data class PrivacyLegalDocuments(
        val baselineCanonicalJson: String,
        val defaultCanonicalJson: String,
        val mosaCanonicalJson: String,
        val serviceCanonicalJson: String,
    )

    private data class TermsLegalDocuments(
        val baselineCanonicalJson: String,
        val defaultCanonicalJson: String,
        val mosaCanonicalJson: String,
        val operatorCanonicalJson: String,
    )

    private data class MandatoryChainContext(
        val baselineYaml: String,
        val defaultYaml: String,
        val mosaYaml: String,
        val conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        val profileCanonicalJsons: List<String>,
    )

    private data class FullChainContext(
        val conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        val profiles: List<ClearTermsProfile>,
        val profileCanonicalJsons: List<String>,
    )

    private data class OperatorProfileTemplateMetadata(
        val profileId: String,
        val profileVersion: String,
        val parentId: String,
        val parentVersion: String,
    )

    private fun createPrivacyLegalDocuments(conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult): PrivacyLegalDocuments =
        PrivacyLegalDocuments(
            baselineCanonicalJson = conversion.baseline.canonicalJson,
            defaultCanonicalJson = conversion.profiles[0].canonicalJson,
            mosaCanonicalJson = conversion.profiles[1].canonicalJson,
            serviceCanonicalJson = conversion.profiles[2].canonicalJson,
        )

    private fun createTermsLegalDocuments(conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult): TermsLegalDocuments =
        TermsLegalDocuments(
            baselineCanonicalJson = conversion.baseline.canonicalJson,
            defaultCanonicalJson = conversion.profiles[0].canonicalJson,
            mosaCanonicalJson = conversion.profiles[1].canonicalJson,
            operatorCanonicalJson = conversion.profiles[2].canonicalJson,
        )

    private companion object {
        private val logger = KotlinLogging.logger {}
        private const val BASELINE_RESOURCE = "clearterms/privacy/privacy-gdpr-baseline.yaml"
        private const val DEFAULT_RESOURCE = "clearterms/privacy/privacy-gdpr-default.yaml"
        private const val MOSA_RESOURCE = "clearterms/privacy/mosa-privacy-gdpr-service.yaml"
        private const val PRIVACY_FALLBACK_PROFILE_ID = "mosa-privacy-fallback-service"
        private const val TERMS_FALLBACK_PROFILE_ID = "mosa-terms-fallback-operator"
        private const val PRIVACY_OPERATOR_PROFILE_ID = "privacy-operator-service"
        private const val PRIVACY_OPERATOR_PROFILE_VERSION = "1.0.0-draft2"
        private const val TERMS_OPERATOR_PROFILE_ID = "terms-operator-service"
        private const val TERMS_OPERATOR_PROFILE_VERSION = "1.0.0-draft2"
        private const val TERMS_BASELINE_RESOURCE = "clearterms/terms/terms-baseline.yaml"
        private const val TERMS_DEFAULT_RESOURCE = "clearterms/terms/terms-default.yaml"
        private const val TERMS_SERVICE_RESOURCE = "clearterms/terms/mosa-terms-service.yaml"
        private val yamlMapper: ObjectMapper =
            ObjectMapper(YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
    }
}
