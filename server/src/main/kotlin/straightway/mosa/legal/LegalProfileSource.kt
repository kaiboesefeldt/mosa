package straightway.mosa.legal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import straightway.mosa.legal.clearterms.yaml.YamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.yaml.YamlLanguageFragmentMerger
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path

internal class LegalProfileSource(
    private val canonicalJsonConverter: YamlCanonicalJsonConverter,
    private val languageFragmentMerger: YamlLanguageFragmentMerger,
    private val resourceTextReader: ResourceTextReader,
    private val resourceDirectoryEntriesProvider: ResourceDirectoryEntriesProvider,
    private val resourceUrlsProvider: ResourceUrlsProvider,
) {
    fun loadMandatoryChainContext(
        kind: LegalDocumentKind,
        baselineResource: String = kind.baselineResource,
        resourceDirectory: String = kind.resourceDirectory,
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

    fun convertFullChain(
        mandatoryContext: MandatoryChainContext,
        operatorYaml: String,
    ): YamlCanonicalJsonConverter.ConversionResult =
        canonicalJsonConverter.convert(
            mandatoryContext.baselineYaml,
            mandatoryContext.profileYamls + operatorYaml,
        )

    fun readOptionalProfile(path: Path): String? {
        val normalizedPath = path.toAbsolutePath().normalize()
        if (!Files.exists(normalizedPath)) {
            return null
        }
        val baseYaml = Files.readString(normalizedPath, Charsets.UTF_8)
        val fragmentYamls = listSiblingFileFragments(normalizedPath).map { fragmentPath -> Files.readString(fragmentPath, Charsets.UTF_8) }
        return languageFragmentMerger.merge(baseYaml, fragmentYamls)
    }

    fun parseOperatorProfileMetadata(
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

    private fun readRequiredResource(path: String): String {
        val baseYaml = readResource(path) ?: throw IllegalStateException("Missing resource '$path'")
        val fragmentYamls = listResourceFragments(path).map(::readRequiredResourceWithoutFragments)
        return languageFragmentMerger.merge(baseYaml, fragmentYamls)
    }

    private fun readRequiredResourceWithoutFragments(path: String): String =
        readResource(path) ?: throw IllegalStateException("Missing resource '$path'")

    private fun readResource(path: String): String? = resourceTextReader.read(path)

    private fun listSiblingFileFragments(basePath: Path): List<Path> {
        val baseFileName = basePath.fileName.toString()
        if (!baseFileName.endsWith(".yaml")) {
            return emptyList()
        }
        val prefix = "${baseFileName.removeSuffix(".yaml")}."
        val parent = basePath.parent
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
        val resources = resourceUrlsProvider.list(directoryPath)
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
        resourceDirectoryEntriesProvider.list(directoryPath)?.let { entries ->
            return entries.sorted()
        }
        val discovered = linkedSetOf<String>()
        val resources = resourceUrlsProvider.list(directoryPath)
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

    private companion object {
        private val yamlMapper: ObjectMapper =
            ObjectMapper(YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
    }
}
