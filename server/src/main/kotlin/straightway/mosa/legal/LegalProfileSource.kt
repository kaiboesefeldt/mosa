package straightway.mosa.legal

import straightway.mosa.legal.clearterms.yaml.YamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.yaml.YamlLanguageFragmentMerger
import java.nio.file.Path

internal class LegalProfileSource(
    private val canonicalJsonConverter: YamlCanonicalJsonConverter,
    private val languageFragmentMerger: YamlLanguageFragmentMerger,
    private val resourceTextReader: ResourceTextReader,
    private val resourceDirectoryEntriesProvider: ResourceDirectoryEntriesProvider,
    private val resourceUrlsProvider: ResourceUrlsProvider,
) {
    private val resourceResolver =
        LegalProfileResourceResolver(
            languageFragmentMerger = languageFragmentMerger,
            resourceTextReader = resourceTextReader,
            resourceDirectoryEntriesProvider = resourceDirectoryEntriesProvider,
            resourceUrlsProvider = resourceUrlsProvider,
        )
    private val metadataParser = LegalProfileMetadataParser()

    fun loadMandatoryChainContext(
        kind: LegalDocumentKind,
        baselineResource: String = kind.baselineResource,
        resourceDirectory: String = kind.resourceDirectory,
    ): MandatoryChainContext {
        val baselineYaml = resourceResolver.readRequiredResource(baselineResource)
        val profileYamls = resolveResourceProfileChain(baselineResource, resourceDirectory)
        val profileMetadata =
            profileYamls.mapIndexed { index, yaml ->
                metadataParser.parseProfileMetadata(yaml, "$resourceDirectory[$index]")
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

    fun readOptionalProfile(path: Path): String? = resourceResolver.readOptionalProfile(path)

    fun parseOperatorProfileMetadata(
        yaml: String,
        path: Path,
    ): OperatorProfileMetadata = metadataParser.parseOperatorProfileMetadata(yaml, path.toString())

    private fun resolveResourceProfileChain(
        baselineResource: String,
        resourceDirectory: String,
    ): List<String> {
        val baselineYaml = resourceResolver.readRequiredResource(baselineResource)
        val baseline = metadataParser.parseBaselineMetadata(baselineYaml, baselineResource)
        val profiles =
            resourceResolver
                .listResourceBaseDocuments(resourceDirectory)
                .filterNot { path -> path == baselineResource }
                .map { path -> ResourceProfileCandidate(path, resourceResolver.readRequiredResource(path)) }
        val profileMetadataByCandidate =
            profiles.associateWith { candidate -> metadataParser.parseProfileMetadata(candidate.yaml, candidate.path) }
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
}
