package straightway.mosa.legal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

internal class LegalProfileMetadataParser {
    fun parseBaselineMetadata(
        yaml: String,
        source: String,
    ): BaselineMetadata {
        val root = parseYamlTree(yaml, source)
        return BaselineMetadata(
            id = root.requiredText("id", source),
            version = root.requiredText("version", source),
        )
    }

    fun parseProfileMetadata(
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

    fun parseOperatorProfileMetadata(
        yaml: String,
        path: String,
    ): OperatorProfileMetadata =
        runCatching {
            val root = parseYamlTree(yaml, path)
            val id = root.requiredText("id", path)
            val version = root.requiredText("version", path)
            val parent = root.requiredObject("parent", path)
            OperatorProfileMetadata(
                id = id,
                version = version,
                referencedParentId = parent.requiredText("id", path),
                referencedParentVersion = parent.requiredText("version", path),
            )
        }.getOrElse {
            OperatorProfileMetadata.missing(path)
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
