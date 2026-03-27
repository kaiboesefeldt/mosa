package straightway.mosa.legal.clearterms.yaml

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import straightway.mosa.legal.clearterms.canonical.CanonicalNodeRenderer
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.legal.clearterms.parse.JsonNode
import straightway.mosa.legal.clearterms.parse.JsonObjectContract
import straightway.mosa.legal.clearterms.validation.ValidationException
import java.security.MessageDigest
import com.fasterxml.jackson.databind.JsonNode as JacksonJsonNode

class YamlCanonicalJsonConverter(
    private val yamlMapper: ObjectMapper = defaultYamlMapper(),
    private val canonicalNodeRenderer: CanonicalNodeRenderer = CanonicalNodeRenderer(),
    private val documentReader: DocumentReader = DocumentReader(),
) {
    fun convert(
        baselineYaml: String,
        profileChainYamls: List<String>,
    ): ConversionResult {
        if (profileChainYamls.isEmpty()) {
            throw ValidationException("At least one profile is required")
        }

        val baselineJson = parseYamlObject(baselineYaml)
        val baseline = documentReader.readBaseline(baselineJson)
        val baselineCanonical = canonicalNodeRenderer.render(baselineJson)
        val baselineHash = hashSha256(baselineCanonical)

        var previousId = baseline.id
        var previousVersion = baseline.version
        var previousHash = baselineHash

        val profiles = mutableListOf<CanonicalArtifact>()
        profileChainYamls.forEach { profileYaml ->
            val profileWithoutHash = parseYamlObject(profileYaml)
            val profileWithHash =
                withInjectedParentHash(
                    profileWithoutHash,
                    previousId,
                    previousVersion,
                    previousHash,
                )
            val parsedProfile = documentReader.readProfile(profileWithHash)
            val canonicalProfile = canonicalNodeRenderer.render(profileWithHash)
            val profileHash = hashSha256(canonicalProfile)
            profiles += CanonicalArtifact(profileWithHash, canonicalProfile, profileHash)
            previousId = parsedProfile.id
            previousVersion = parsedProfile.version
            previousHash = profileHash
        }

        return ConversionResult(
            baseline = CanonicalArtifact(baselineJson, baselineCanonical, baselineHash),
            profiles = profiles,
        )
    }

    private fun withInjectedParentHash(
        profileJson: JsonNode.ObjectNode,
        expectedParentId: String,
        expectedParentVersion: String,
        expectedParentHash: String,
    ): JsonNode.ObjectNode {
        val profileContract = JsonObjectContract(profileJson)
        profileContract.requireNoExtraKeys(profileKeys)
        profileContract.requireKeys(profileKeys)

        val parent = profileContract.requireMap("parent")
        val parentContract = JsonObjectContract(parent)
        parentContract.requireNoExtraKeys(parentKeys)
        parentContract.requireKeys(parentKeys)

        val parentId = parentContract.requireString("id")
        val parentVersion = parentContract.requireString("version")
        if (parentId != expectedParentId || parentVersion != expectedParentVersion) {
            throw ValidationException("Profile parent must reference previous artifact id/version")
        }

        val entries =
            profileJson.entries.toMutableMap().apply {
                this["parent"] =
                    JsonNode.ObjectNode(
                        mapOf(
                            "id" to JsonNode.ScalarNode.StringNode(parentId),
                            "version" to JsonNode.ScalarNode.StringNode(parentVersion),
                            "hash" to JsonNode.ScalarNode.StringNode(expectedParentHash),
                        ),
                    )
            }
        return JsonNode.ObjectNode(entries)
    }

    private fun parseYamlObject(yaml: String): JsonNode.ObjectNode {
        val tree =
            try {
                yamlMapper.readTree(yaml)
            } catch (exception: Exception) {
                throw ValidationException("Invalid YAML document: ${exception.message}")
            }
        val node = fromJacksonNode(tree)
        return node as? JsonNode.ObjectNode
            ?: throw ValidationException("Root document must be a mapping")
    }

    private fun fromJacksonNode(node: JacksonJsonNode): JsonNode =
        when {
            node.isObject -> {
                val entries = linkedMapOf<String, JsonNode>()
                node.properties().forEach { (key, value) ->
                    entries[key] = fromJacksonNode(value)
                }
                JsonNode.ObjectNode(entries)
            }

            node.isArray -> {
                JsonNode.ArrayNode(node.map { item -> fromJacksonNode(item) })
            }

            node.isTextual -> {
                JsonNode.ScalarNode.StringNode(node.asText())
            }

            node.isNumber -> {
                JsonNode.ScalarNode.NumberNode(node.asDouble())
            }

            node.isBoolean -> {
                JsonNode.ScalarNode.BooleanNode(node.asBoolean())
            }

            node.isNull -> {
                JsonNode.ScalarNode.NullNode
            }

            else -> {
                throw ValidationException("Only string, number, boolean and null values are supported")
            }
        }

    private fun hashSha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hashHex = digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "sha256:$hashHex"
    }

    data class ConversionResult(
        val baseline: CanonicalArtifact,
        val profiles: List<CanonicalArtifact>,
    ) {
        fun canonicalJsonChain(): List<String> = listOf(baseline.canonicalJson) + profiles.map { it.canonicalJson }
    }

    data class CanonicalArtifact(
        val json: JsonNode.ObjectNode,
        val canonicalJson: String,
        val hash: String,
    )

    private companion object {
        private val profileKeys = setOf("kind", "id", "version", "parent", "overrides")
        private val parentKeys = setOf("id", "version")

        private fun defaultYamlMapper(): ObjectMapper =
            ObjectMapper(YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
    }
}
