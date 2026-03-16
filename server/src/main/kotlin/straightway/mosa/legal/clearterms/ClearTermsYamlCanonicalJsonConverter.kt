package straightway.mosa.legal.clearterms

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.security.MessageDigest

class ClearTermsYamlCanonicalJsonConverter(
    private val yamlMapper: ObjectMapper = defaultYamlMapper(),
    private val canonicalJson: ClearTermsCanonicalJson = ClearTermsCanonicalJson(),
    private val documentReader: ClearTermsDocumentReader = ClearTermsDocumentReader(),
) {
    fun convert(
        baselineYaml: String,
        profileChainYamls: List<String>,
    ): ConversionResult {
        if (profileChainYamls.isEmpty()) {
            throw ClearTermsValidationException("At least one profile is required")
        }

        val baselineJson = parseYamlObject(baselineYaml)
        val baseline = documentReader.readBaseline(baselineJson)
        val baselineCanonical = canonicalJson.canonicalize(baselineJson)
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
            val canonicalProfile = canonicalJson.canonicalize(profileWithHash)
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
        profileJson: ClearTermsJsonNode.ObjectNode,
        expectedParentId: String,
        expectedParentVersion: String,
        expectedParentHash: String,
    ): ClearTermsJsonNode.ObjectNode {
        val profileContract = ClearTermsJsonObjectContract(profileJson)
        profileContract.requireNoExtraKeys(profileKeys)
        profileContract.requireKeys(profileKeys)

        val parent = profileContract.requireMap("parent")
        val parentContract = ClearTermsJsonObjectContract(parent)
        parentContract.requireNoExtraKeys(parentKeys)
        parentContract.requireKeys(parentKeys)

        val parentId = parentContract.requireString("id")
        val parentVersion = parentContract.requireString("version")
        if (parentId != expectedParentId || parentVersion != expectedParentVersion) {
            throw ClearTermsValidationException("Profile parent must reference previous artifact id/version")
        }

        val entries =
            profileJson.entries.toMutableMap().apply {
                this["parent"] =
                    ClearTermsJsonNode.ObjectNode(
                        mapOf(
                            "id" to ClearTermsJsonNode.ScalarNode.StringNode(parentId),
                            "version" to ClearTermsJsonNode.ScalarNode.StringNode(parentVersion),
                            "hash" to ClearTermsJsonNode.ScalarNode.StringNode(expectedParentHash),
                        ),
                    )
            }
        return ClearTermsJsonNode.ObjectNode(entries)
    }

    private fun parseYamlObject(yaml: String): ClearTermsJsonNode.ObjectNode {
        val tree =
            try {
                yamlMapper.readTree(yaml)
            } catch (exception: Exception) {
                throw ClearTermsValidationException("Invalid YAML document: ${exception.message}")
            }
        val node = fromJacksonNode(tree)
        return node as? ClearTermsJsonNode.ObjectNode
            ?: throw ClearTermsValidationException("Root document must be a mapping")
    }

    private fun fromJacksonNode(node: JsonNode): ClearTermsJsonNode =
        when {
            node.isObject -> {
                val entries = linkedMapOf<String, ClearTermsJsonNode>()
                node.properties().forEach { (key, value) ->
                    entries[key] = fromJacksonNode(value)
                }
                ClearTermsJsonNode.ObjectNode(entries)
            }

            node.isArray -> {
                ClearTermsJsonNode.ArrayNode(node.map { item -> fromJacksonNode(item) })
            }

            node.isTextual -> {
                ClearTermsJsonNode.ScalarNode.StringNode(node.asText())
            }

            node.isNumber -> {
                ClearTermsJsonNode.ScalarNode.NumberNode(node.asDouble())
            }

            node.isBoolean -> {
                ClearTermsJsonNode.ScalarNode.BooleanNode(node.asBoolean())
            }

            node.isNull -> {
                ClearTermsJsonNode.ScalarNode.NullNode
            }

            else -> {
                throw ClearTermsValidationException("Only string, number, boolean and null values are supported")
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
        val json: ClearTermsJsonNode.ObjectNode,
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
