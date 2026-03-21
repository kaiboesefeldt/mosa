package straightway.mosa.legal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
    private val privacyResourceDirectory: String = PRIVACY_RESOURCE_DIRECTORY,
    private val termsResourceDirectory: String = TERMS_RESOURCE_DIRECTORY,
    private val resourceTextReader: (String) -> String? = { path ->
        LegalService::class.java.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
    },
    private val resourceDirectoryEntriesProvider: (String) -> List<String>? = { _ -> null },
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
            documentKind = "privacy",
            profilePath = operatorProfilePath,
            mandatoryContext = loadMandatoryChainContext(),
        )
        validateOperatorProfileForStartup(
            documentKind = "terms",
            profilePath = termsOperatorProfilePath,
            mandatoryContext =
                loadMandatoryChainContext(
                    baselineResource = TERMS_BASELINE_RESOURCE,
                    resourceDirectory = termsResourceDirectory,
                ),
        )
        privacyServiceCanonicalJson()
        termsOperatorCanonicalJson()
    }

    fun privacyBaselineCanonicalJson(): String = documents.baselineCanonicalJson

    fun privacyDefaultCanonicalJson(): String = documents.defaultCanonicalJson

    fun privacyMosaCanonicalJson(): String = documents.mosaCanonicalJson

    fun privacyServiceCanonicalJson(): String = documents.serviceCanonicalJson

    fun privacyDocumentChainResponse(): LegalDocumentChainResponse = documents.toDocumentChainResponse()

    fun termsBaselineCanonicalJson(): String = termsDocuments.baselineCanonicalJson

    fun termsDefaultCanonicalJson(): String = termsDocuments.defaultCanonicalJson

    fun termsMosaCanonicalJson(): String = termsDocuments.mosaCanonicalJson

    fun termsOperatorCanonicalJson(): String = termsDocuments.operatorCanonicalJson

    fun termsDocumentChainResponse(): LegalDocumentChainResponse = termsDocuments.toDocumentChainResponse()

    private fun loadPrivacyDocuments(): PrivacyLegalDocuments {
        val mandatoryContext = loadMandatoryChainContext()
        val operatorYaml =
            readOptionalProfile(operatorProfilePath) ?: run {
                createMissingOperatorProfileFile(
                    profilePath = operatorProfilePath,
                    mandatoryContext = mandatoryContext,
                    fallbackProfileId = PRIVACY_FALLBACK_PROFILE_ID,
                )
                failForMissingOrIncompleteOperatorProfile(
                    reason = "Operator profile file is missing at '${
                        operatorProfilePath.toAbsolutePath().normalize()
                    }'",
                    affectedProfilePath = operatorProfilePath,
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
                resourceDirectory = termsResourceDirectory,
            )
        val operatorYaml =
            readOptionalProfile(termsOperatorProfilePath) ?: run {
                createMissingOperatorProfileFile(
                    profilePath = termsOperatorProfilePath,
                    mandatoryContext = mandatoryContext,
                    fallbackProfileId = TERMS_FALLBACK_PROFILE_ID,
                )
                failForMissingOrIncompleteOperatorProfile(
                    reason = "Operator profile file is missing at '${termsOperatorProfilePath.toAbsolutePath().normalize()}'",
                    affectedProfilePath = termsOperatorProfilePath,
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
                    reason =
                        "Operator profile at '${termsOperatorProfilePath.toAbsolutePath().normalize()}' " +
                            "is invalid and could not be converted: ${exception.message}",
                    affectedProfilePath = termsOperatorProfilePath,
                    operatorValuesOverride = extractOperatorValuesBestEffort(operatorYaml),
                )
            }
        val fullChainContext = createFullChainContext(fullConversion)
        fallbackForInvalidFullChain(
            fullChainContext = fullChainContext,
            affectedProfilePath = termsOperatorProfilePath,
        )
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
                    reason =
                        "Operator profile at '${operatorProfilePath.toAbsolutePath().normalize()}' " +
                            "is invalid and could not be converted: ${exception.message}",
                    affectedProfilePath = operatorProfilePath,
                    operatorValuesOverride = extractOperatorValuesBestEffort(operatorYaml),
                )
            }
        val fullChainContext = createFullChainContext(fullConversion)
        fallbackForInvalidFullChain(
            fullChainContext = fullChainContext,
            affectedProfilePath = operatorProfilePath,
        )
        return createPrivacyLegalDocuments(fullConversion)
    }

    private fun loadMandatoryChainContext(): MandatoryChainContext =
        loadMandatoryChainContext(
            baselineResource = BASELINE_RESOURCE,
            resourceDirectory = privacyResourceDirectory,
        )

    private fun loadMandatoryChainContext(
        baselineResource: String,
        resourceDirectory: String,
    ): MandatoryChainContext {
        val baselineYaml = readRequiredResource(baselineResource)
        val profileYamls = resolveResourceProfileChain(baselineResource, resourceDirectory)
        val profileMetadata =
            profileYamls.mapIndexed { index, yaml ->
                parseProfileMetadata(yaml, "$resourceDirectory[$index]")
            }
        val mandatoryConversion =
            canonicalJsonConverter.convert(
                baselineYaml,
                profileYamls,
            )
        return MandatoryChainContext(
            baselineYaml = baselineYaml,
            profileYamls = profileYamls,
            conversion = mandatoryConversion,
            profileCanonicalJsons = mandatoryConversion.profiles.map { artifact -> artifact.canonicalJson },
            lastProfile = profileMetadata.last(),
            availableProfileVersions =
                profileMetadata.groupBy { metadata -> metadata.id }.mapValues { (_, metadata) ->
                    metadata.map { item -> item.version }.distinct().sorted()
                },
        )
    }

    private fun convertFullChain(
        mandatoryContext: MandatoryChainContext,
        operatorYaml: String,
    ): ClearTermsYamlCanonicalJsonConverter.ConversionResult =
        canonicalJsonConverter.convert(
            mandatoryContext.baselineYaml,
            mandatoryContext.profileYamls + operatorYaml,
        )

    private fun validateOperatorProfileForStartup(
        documentKind: String,
        profilePath: Path,
        mandatoryContext: MandatoryChainContext,
    ) {
        val normalizedPath = profilePath.toAbsolutePath().normalize()
        val operatorYaml =
            readOptionalProfile(profilePath)
                ?: throw IllegalStateException(
                    startupValidationMessage(
                        documentKind = documentKind,
                        operatorMetadata = OperatorProfileMetadata.missing(normalizedPath.toString()),
                        reason = "operator profile file is missing at '$normalizedPath'",
                        availableVersions = mandatoryContext.availableProfileVersionsText(),
                    ),
                )
        val fullConversion =
            try {
                convertFullChain(
                    mandatoryContext = mandatoryContext,
                    operatorYaml = operatorYaml,
                )
            } catch (exception: ClearTermsValidationException) {
                throw IllegalStateException(
                    startupValidationMessage(
                        documentKind = documentKind,
                        operatorMetadata = parseOperatorProfileMetadata(operatorYaml, normalizedPath),
                        reason =
                            "operator profile at '$normalizedPath' " +
                                "is invalid and could not be converted: ${exception.message}",
                        availableVersions = mandatoryContext.availableProfileVersionsText(),
                    ),
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
                startupValidationMessage(
                    documentKind = documentKind,
                    operatorMetadata = parseOperatorProfileMetadata(operatorYaml, normalizedPath),
                    reason = "legal profile chain is incomplete for '$normalizedPath'$incompleteDetails",
                    availableVersions = mandatoryContext.availableProfileVersionsText(),
                ),
            )
        }
        val valueTypeValidationError =
            validateChainValues(
                baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
                profiles = fullChainContext.profiles,
                requireAll = false,
            )
        check(valueTypeValidationError == null) {
            startupValidationMessage(
                documentKind = documentKind,
                operatorMetadata = parseOperatorProfileMetadata(operatorYaml, normalizedPath),
                reason =
                    "legal profile chain has invalid placeholder values " +
                        "in '$normalizedPath': ${valueTypeValidationError?.message}",
                availableVersions = mandatoryContext.availableProfileVersionsText(),
            )
        }
        val missingRequiredValuesError = missingRequiredChainValuesError(fullChainContext)
        check(missingRequiredValuesError == null) {
            startupValidationMessage(
                documentKind = documentKind,
                operatorMetadata = parseOperatorProfileMetadata(operatorYaml, normalizedPath),
                reason =
                    "legal profile chain has invalid placeholder values " +
                        "in '$normalizedPath': ${missingRequiredValuesError?.message}",
                availableVersions = mandatoryContext.availableProfileVersionsText(),
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
        fullChainContext: FullChainContext,
        affectedProfilePath: Path,
    ) {
        val analysis = analyzeChainCompleteness(fullChainContext.conversion.baseline.canonicalJson, fullChainContext.profileCanonicalJsons)
        if (!analysis.complete) {
            failForMissingOrIncompleteOperatorProfile(
                reason = "Legal profile chain is incomplete for '${affectedProfilePath.toAbsolutePath().normalize()}'",
                affectedProfilePath = affectedProfilePath,
            )
        }
        throwWhenChainHasInvalidValueTypes(fullChainContext)
        val fullChainValidationError =
            missingRequiredChainValuesError(fullChainContext) ?: return
        failForMissingOrIncompleteOperatorProfile(
            reason =
                "Legal profile chain has invalid placeholder values in " +
                    "'${affectedProfilePath.toAbsolutePath().normalize()}': ${fullChainValidationError.message}",
            affectedProfilePath = affectedProfilePath,
        )
    }

    private fun throwWhenChainHasInvalidValueTypes(fullChainContext: FullChainContext) {
        val valueTypeValidationError =
            validateChainValues(
                baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
                profiles = fullChainContext.profiles,
                requireAll = false,
            )
        check(
            valueTypeValidationError == null,
        ) { "Legal profile chain has invalid placeholder values: ${valueTypeValidationError?.message}" }
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

    private fun readResource(path: String): String? = resourceTextReader(path)

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
        val directoryPath = basePath.substringBeforeLast('/', "")
        val discovered = linkedSetOf<String>()
        val resources = javaClass.classLoader.getResources(directoryPath)
        while (resources.hasMoreElements()) {
            val directoryUrl = resources.nextElement()
            val fragments =
                when (directoryUrl.protocol) {
                    "file" -> {
                        listFileResourceFragments(directoryUrl.toURI(), directoryPath, baseFileName)
                    }

                    "jar" -> {
                        listJarResourceFragments(
                            directoryUrl.openConnection() as JarURLConnection,
                            directoryPath,
                            baseFileName,
                        )
                    }

                    else -> {
                        emptyList()
                    }
                }
            fragments.forEach(discovered::add)
        }
        return discovered.toList()
    }

    private fun listFileResourceFragments(
        directoryUri: java.net.URI,
        directoryPath: String,
        baseFileName: String,
    ): List<String> {
        val directory = Path.of(directoryUri)
        return Files
            .list(directory)
            .use { stream ->
                stream
                    .filter { candidate ->
                        Files.isRegularFile(candidate) && isMatchingFragmentName(candidate.fileName.toString(), baseFileName)
                    }.map { candidate -> toResourcePath(directoryPath, candidate.fileName.toString()) }
                    .sorted()
                    .toList()
            }
    }

    private fun listJarResourceFragments(
        connection: JarURLConnection,
        directoryPath: String,
        baseFileName: String,
    ): List<String> {
        val directoryEntryPrefix = connection.entryName.trimEnd('/') + "/"
        return connection
            .jarFile
            .entries()
            .asSequence()
            .map { entry -> entry.name }
            .filter { entryName ->
                entryName.startsWith(directoryEntryPrefix) &&
                    !entryName.removePrefix(directoryEntryPrefix).contains("/")
            }.map { entryName -> entryName.removePrefix(directoryEntryPrefix) }
            .filter { name -> isMatchingFragmentName(name, baseFileName) }
            .map { name -> toResourcePath(directoryPath, name) }
            .sorted()
            .toList()
    }

    private fun isMatchingFragmentName(
        candidateFileName: String,
        baseFileName: String,
    ): Boolean =
        candidateFileName != baseFileName && candidateFileName.startsWith(fragmentPrefix(baseFileName)) &&
            candidateFileName.endsWith(".yaml")

    private fun fragmentPrefix(baseFileName: String): String = "${baseFileName.removeSuffix(".yaml")}."

    private fun toResourcePath(
        directoryPath: String,
        fileName: String,
    ): String =
        if (directoryPath.isEmpty()) {
            fileName
        } else {
            "$directoryPath/$fileName"
        }

    private fun resolveResourceProfileChain(
        baselineResource: String,
        resourceDirectory: String,
    ): List<String> {
        val baselineYaml = readRequiredResource(baselineResource)
        val baseline = parseBaselineMetadata(baselineYaml, baselineResource)
        val profiles =
            listResourceBaseDocuments(resourceDirectory)
                .filterNot { path -> path == baselineResource }
                .map { path -> ResourceProfileCandidate(path, readRequiredResource(path)) }
        val profileMetadataByCandidate = profiles.associateWith { candidate -> parseProfileMetadata(candidate.yaml, candidate.path) }
        val resolved = mutableListOf<ResourceProfileCandidate>()
        val unresolved = profileMetadataByCandidate.toMutableMap()
        var expectedParent = baseline.reference
        while (true) {
            val next =
                unresolved.entries.singleOrNull { (_, metadata) ->
                    metadata.parent == expectedParent
                } ?: break
            resolved += next.key
            unresolved.remove(next.key)
            expectedParent = next.value.reference
        }
        check(resolved.isNotEmpty()) { "No legal profiles found in '$resourceDirectory'" }
        check(unresolved.isEmpty()) {
            "Legal profile resources in '$resourceDirectory' do not form a single deterministic parent chain"
        }
        return resolved.map { candidate -> candidate.yaml }
    }

    private fun listResourceBaseDocuments(directoryPath: String): List<String> =
        listResourceFiles(directoryPath)
            .filter { path ->
                path.endsWith(".yaml") && path.substringAfterLast('/').count { character -> character == '.' } == 1
            }.sorted()

    private fun listResourceFiles(directoryPath: String): List<String> {
        resourceDirectoryEntriesProvider(directoryPath)?.let { entries ->
            return entries.sorted()
        }
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
                                .filter(Files::isRegularFile)
                                .map { candidate ->
                                    if (directoryPath.isEmpty()) {
                                        candidate.fileName.toString()
                                    } else {
                                        "$directoryPath/${candidate.fileName}"
                                    }
                                }.sorted()
                                .forEach(discovered::add)
                        }
                }

                "jar" -> {
                    val connection = directoryUrl.openConnection() as JarURLConnection
                    val directoryEntryPrefix = connection.entryName.trimEnd('/') + "/"
                    connection
                        .jarFile
                        .entries()
                        .asSequence()
                        .map { entry -> entry.name }
                        .filter { entryName ->
                            entryName.startsWith(directoryEntryPrefix) &&
                                !entryName.removePrefix(directoryEntryPrefix).contains("/")
                        }.sorted()
                        .forEach(discovered::add)
                }
            }
        }
        return discovered.toList()
    }

    private fun parseBaselineMetadata(
        yaml: String,
        source: String,
    ): BaselineMetadata {
        val root = parseYamlTree(yaml, source)
        return BaselineMetadata(
            id = root.requiredText("id", source),
            version = root.requiredText("version", source),
        )
    }

    private fun parseOperatorProfileMetadata(
        yaml: String,
        path: Path,
    ): OperatorProfileMetadata =
        runCatching {
            val root = parseYamlTree(yaml, path.toString())
            val id = root.requiredText("id", path.toString())
            val version = root.requiredText("version", path.toString())
            val parent = root.requiredObject("parent", path.toString())
            OperatorProfileMetadata(
                id = id,
                version = version,
                referencedParentId = parent.requiredText("id", path.toString()),
                referencedParentVersion = parent.requiredText("version", path.toString()),
            )
        }.getOrElse {
            OperatorProfileMetadata.missing(path.toString())
        }

    private fun parseProfileMetadata(
        yaml: String,
        source: String,
    ): ProfileMetadata {
        val root = parseYamlTree(yaml, source)
        val parent = root.requiredObject("parent", source)
        return ProfileMetadata(
            id = root.requiredText("id", source),
            version = root.requiredText("version", source),
            parent =
                ArtifactReference(
                    id = parent.requiredText("id", source),
                    version = parent.requiredText("version", source),
                ),
        )
    }

    private fun parseYamlTree(
        yaml: String,
        source: String,
    ) = try {
        yamlMapper.readTree(yaml)
    } catch (exception: Exception) {
        throw IllegalStateException("Invalid YAML in '$source': ${exception.message}", exception)
    }

    private fun com.fasterxml.jackson.databind.JsonNode.requiredObject(
        key: String,
        source: String,
    ): com.fasterxml.jackson.databind.JsonNode {
        val node = get(key)
        check(node != null && node.isObject) { "Expected object key '$key' in '$source'" }
        return node
    }

    private fun com.fasterxml.jackson.databind.JsonNode.requiredText(
        key: String,
        source: String,
    ): String {
        val node = get(key)
        check(node != null && node.isTextual) { "Expected text key '$key' in '$source'" }
        return node.asText()
    }

    private fun startupValidationMessage(
        documentKind: String,
        operatorMetadata: OperatorProfileMetadata,
        reason: String,
        availableVersions: String,
    ): String =
        "Legal operator profile is missing or incomplete: " +
            "documentKind=$documentKind; " +
            "targetOperatorProfile=${operatorMetadata.id}@${operatorMetadata.version}; " +
            "referencedParent=${operatorMetadata.referencedParentId}@${operatorMetadata.referencedParentVersion}; " +
            "reason=$reason; " +
            "availableVersions=$availableVersions"

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
                    resourceDirectory = termsResourceDirectory,
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
            appendLine("""  id: "${mandatoryContext.lastProfile.id}"""")
            appendLine("""  version: "${mandatoryContext.lastProfile.version}"""")
            append(overridesYaml)
        }
    }

    private fun templateMetadataFor(fallbackProfileId: String): OperatorProfileTemplateMetadata =
        when (fallbackProfileId) {
            PRIVACY_FALLBACK_PROFILE_ID -> {
                OperatorProfileTemplateMetadata(
                    profileId = PRIVACY_OPERATOR_PROFILE_ID,
                    profileVersion = PRIVACY_OPERATOR_PROFILE_VERSION,
                )
            }

            TERMS_FALLBACK_PROFILE_ID -> {
                OperatorProfileTemplateMetadata(
                    profileId = TERMS_OPERATOR_PROFILE_ID,
                    profileVersion = TERMS_OPERATOR_PROFILE_VERSION,
                )
            }

            else -> {
                throw IllegalArgumentException("Unsupported fallback profile id '$fallbackProfileId'")
            }
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

    private fun fallbackToMosaServiceProfile(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        reason: String,
        affectedProfilePath: Path,
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
                fallbackProfileId = PRIVACY_FALLBACK_PROFILE_ID,
            )
        return PrivacyLegalDocuments(
            baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
            profileCanonicalJsons = mandatoryConversion.profiles.map { it.canonicalJson } + fallbackServiceCanonicalJson,
            profileCanonicalJsonById =
                mandatoryConversion.profiles.associate { artifact -> artifact.profileId() to artifact.canonicalJson } +
                    (reader.readProfile(parser.parse(fallbackServiceCanonicalJson)).id to fallbackServiceCanonicalJson),
        )
    }

    private fun fallbackToTermsOperatorProfile(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        reason: String,
        affectedProfilePath: Path,
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
                fallbackProfileId = TERMS_FALLBACK_PROFILE_ID,
            )
        return TermsLegalDocuments(
            baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
            profileCanonicalJsons = mandatoryConversion.profiles.map { it.canonicalJson } + fallbackOperatorCanonicalJson,
            profileCanonicalJsonById =
                mandatoryConversion.profiles.associate { artifact -> artifact.profileId() to artifact.canonicalJson } +
                    (reader.readProfile(parser.parse(fallbackOperatorCanonicalJson)).id to fallbackOperatorCanonicalJson),
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

    private data class PrivacyLegalDocuments(
        val baselineCanonicalJson: String,
        val profileCanonicalJsons: List<String>,
        val profileCanonicalJsonById: Map<String, String>,
    ) {
        val defaultCanonicalJson: String get() = requireProfileCanonicalJson(PRIVACY_DEFAULT_PROFILE_ID)
        val mosaCanonicalJson: String get() = requireProfileCanonicalJson(PRIVACY_MOSA_PROFILE_ID)
        val serviceCanonicalJson: String get() = profileCanonicalJsons.last()

        fun toDocumentChainResponse(): LegalDocumentChainResponse =
            LegalDocumentChainResponse(
                baseline = jsonObjectOf(baselineCanonicalJson),
                profiles = profileCanonicalJsons.map(::jsonObjectOf),
            )

        private fun requireProfileCanonicalJson(profileId: String): String =
            profileCanonicalJsonById[profileId]
                ?: throw IllegalStateException("Missing required privacy profile '$profileId'")
    }

    private data class TermsLegalDocuments(
        val baselineCanonicalJson: String,
        val profileCanonicalJsons: List<String>,
        val profileCanonicalJsonById: Map<String, String>,
    ) {
        val defaultCanonicalJson: String get() = requireProfileCanonicalJson(TERMS_DEFAULT_PROFILE_ID)
        val mosaCanonicalJson: String get() = requireProfileCanonicalJson(TERMS_MOSA_PROFILE_ID)
        val operatorCanonicalJson: String get() = profileCanonicalJsons.last()

        fun toDocumentChainResponse(): LegalDocumentChainResponse =
            LegalDocumentChainResponse(
                baseline = jsonObjectOf(baselineCanonicalJson),
                profiles = profileCanonicalJsons.map(::jsonObjectOf),
            )

        private fun requireProfileCanonicalJson(profileId: String): String =
            profileCanonicalJsonById[profileId]
                ?: throw IllegalStateException("Missing required terms profile '$profileId'")
    }

    private data class MandatoryChainContext(
        val baselineYaml: String,
        val profileYamls: List<String>,
        val conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        val profileCanonicalJsons: List<String>,
        val lastProfile: ProfileMetadata,
        val availableProfileVersions: Map<String, List<String>>,
    ) {
        fun availableProfileVersionsText(): String =
            availableProfileVersions.entries
                .sortedBy { (id, _) -> id }
                .joinToString("; ") { (id, versions) -> "$id=[${versions.joinToString(", ")}]" }
    }

    private data class FullChainContext(
        val conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        val profiles: List<ClearTermsProfile>,
        val profileCanonicalJsons: List<String>,
    )

    private data class ArtifactReference(
        val id: String,
        val version: String,
    )

    private data class BaselineMetadata(
        val id: String,
        val version: String,
    ) {
        val reference: ArtifactReference = ArtifactReference(id, version)
    }

    private data class ProfileMetadata(
        val id: String,
        val version: String,
        val parent: ArtifactReference,
    ) {
        val reference: ArtifactReference = ArtifactReference(id, version)
    }

    private data class OperatorProfileMetadata(
        val id: String,
        val version: String,
        val referencedParentId: String,
        val referencedParentVersion: String,
    ) {
        companion object {
            fun missing(path: String): OperatorProfileMetadata =
                OperatorProfileMetadata(
                    id = path,
                    version = "unknown",
                    referencedParentId = "unknown",
                    referencedParentVersion = "unknown",
                )
        }
    }

    private data class ResourceProfileCandidate(
        val path: String,
        val yaml: String,
    )

    private data class OperatorProfileTemplateMetadata(
        val profileId: String,
        val profileVersion: String,
    )

    private fun createPrivacyLegalDocuments(conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult): PrivacyLegalDocuments =
        PrivacyLegalDocuments(
            baselineCanonicalJson = conversion.baseline.canonicalJson,
            profileCanonicalJsons = conversion.profiles.map { it.canonicalJson },
            profileCanonicalJsonById = conversion.profiles.associate { artifact -> artifact.profileId() to artifact.canonicalJson },
        )

    private fun createTermsLegalDocuments(conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult): TermsLegalDocuments =
        TermsLegalDocuments(
            baselineCanonicalJson = conversion.baseline.canonicalJson,
            profileCanonicalJsons = conversion.profiles.map { it.canonicalJson },
            profileCanonicalJsonById = conversion.profiles.associate { artifact -> artifact.profileId() to artifact.canonicalJson },
        )

    private companion object {
        private val logger = KotlinLogging.logger {}
        private const val PRIVACY_RESOURCE_DIRECTORY = "clearterms/privacy"
        private const val BASELINE_RESOURCE = "clearterms/privacy/privacy-gdpr-baseline.yaml"
        private const val PRIVACY_DEFAULT_PROFILE_ID = "privacy-gdpr-default"
        private const val PRIVACY_MOSA_PROFILE_ID = "mosa-privacy-gdpr-service"
        private const val PRIVACY_FALLBACK_PROFILE_ID = "mosa-privacy-fallback-service"
        private const val TERMS_FALLBACK_PROFILE_ID = "mosa-terms-fallback-operator"
        private const val PRIVACY_OPERATOR_PROFILE_ID = "privacy-operator-service"
        private const val PRIVACY_OPERATOR_PROFILE_VERSION = "1.0.0-draft2"
        private const val TERMS_DEFAULT_PROFILE_ID = "terms-default"
        private const val TERMS_MOSA_PROFILE_ID = "mosa-terms-service"
        private const val TERMS_OPERATOR_PROFILE_ID = "terms-operator-service"
        private const val TERMS_OPERATOR_PROFILE_VERSION = "1.0.0-draft2"
        private const val TERMS_RESOURCE_DIRECTORY = "clearterms/terms"
        private const val TERMS_BASELINE_RESOURCE = "clearterms/terms/terms-baseline.yaml"
        private val yamlMapper: ObjectMapper =
            ObjectMapper(YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)

        private fun jsonObjectOf(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject
    }

    private fun ClearTermsYamlCanonicalJsonConverter.CanonicalArtifact.profileId(): String =
        (json.entries.getValue("id") as ClearTermsJsonNode.ScalarNode.StringNode).value
}
