package straightway.mosa.legal.clearterms.parse

import straightway.mosa.legal.clearterms.document.Baseline
import straightway.mosa.legal.clearterms.document.Profile
import straightway.mosa.legal.clearterms.document.Reference
import straightway.mosa.legal.clearterms.document.TextBlock
import straightway.mosa.legal.clearterms.document.TextBlockHints
import straightway.mosa.legal.clearterms.document.TextOverrideSeverity
import straightway.mosa.legal.clearterms.validation.ValidationException

class DocumentReader {
    fun readBaseline(root: JsonNode.ObjectNode): Baseline {
        val contract = root.contractWithRequiredKeys(baselineKeys)
        requireDocumentKind(contract, "baseline", "Invalid baseline kind")
        val id = contract.requireString("id")
        val version = contract.requireString("version")
        val textsNode = contract.requireMap("texts")
        if (textsNode.entries.isEmpty()) {
            throw ValidationException("Baseline must define at least one language")
        }
        val textsByLanguage = textsNode.entries.mapValues { (language, node) -> readLanguageBlocks(language, node) }
        return Baseline(id, version, textsByLanguage)
    }

    fun readProfile(root: JsonNode.ObjectNode): Profile {
        val contract = root.contractWithRequiredKeys(profileKeys)
        requireDocumentKind(contract, "profile", "Invalid profile kind")
        val id = contract.requireString("id")
        val version = contract.requireString("version")
        val parentReference = readReference(contract.requireMap("parent"))

        val overridesContract = contract.requireMap("overrides").contractWithRequiredKeys(overrideKeys, emptySet())
        val values =
            overridesContract
                .optionalNode("values")
                ?.let { node ->
                    val map =
                        node as? JsonNode.ObjectNode
                            ?: throw ValidationException("Key 'values' must be a mapping")
                    readScalarMap(map, "Override values must be scalar")
                }
        val texts = overridesContract.optionalNode("texts")?.let { parseProfileTextOverrides(it) }

        return Profile(id, version, parentReference, Profile.Overrides(values, texts))
    }

    private fun requireDocumentKind(
        contract: JsonObjectContract,
        expected: String,
        errorMessage: String,
    ) {
        if (contract.requireString("kind") != expected) {
            throw ValidationException(errorMessage)
        }
    }

    private fun readReference(node: JsonNode.ObjectNode): Reference {
        val contract = node.contractWithRequiredKeys(referenceKeys)
        return Reference(
            id = contract.requireString("id"),
            version = contract.requireString("version"),
            hash = contract.requireString("hash"),
        )
    }

    private fun readScalarMap(
        node: JsonNode.ObjectNode,
        @Suppress("SameParameterValue") errorMessage: String,
    ): Map<String, JsonNode.ScalarNode> =
        node.entries.mapValues { (_, value) ->
            val scalar =
                value as? JsonNode.ScalarNode
                    ?: throw ValidationException(errorMessage)
            when (scalar) {
                is JsonNode.ScalarNode.StringNode -> scalar
                is JsonNode.ScalarNode.NumberNode -> scalar
                JsonNode.ScalarNode.NullNode -> scalar
                is JsonNode.ScalarNode.BooleanNode -> throw ValidationException(errorMessage)
            }
        }

    private fun parseProfileTextOverrides(node: JsonNode): Map<String, Profile.TextOverrides> {
        val map =
            node as? JsonNode.ObjectNode
                ?: throw ValidationException("Overrides texts must be a mapping")

        return map.entries.mapValues { (language, entryNode) ->
            val languageMap =
                entryNode as? JsonNode.ObjectNode
                    ?: throw ValidationException("Overrides for '$language' must be a mapping")
            val languageContract = languageMap.contractWithRequiredKeys(textOverrideKeys, setOf("replace"))

            val replace =
                languageContract.requireMap("replace").entries.mapValues { (blockId, replaceValue) ->
                    val textNode =
                        replaceValue as? JsonNode.ScalarNode.StringNode
                            ?: throw ValidationException(
                                "Replace text for block '$blockId' in language '$language' must be string",
                            )
                    textNode.value
                }

            val append = languageContract.optionalNode("append")?.let { parseAppendBlocks(it) }

            Profile.TextOverrides(replace, append)
        }
    }

    private fun parseAppendBlocks(node: JsonNode): List<TextBlock> {
        val list =
            node as? JsonNode.ArrayNode
                ?: throw ValidationException("Key 'append' must be a list")
        return list.items.map { item ->
            readTextBlock(
                item,
                "Append text must be a mapping",
                allowNullText = false,
                allowHints = false,
            )
        }
    }

    private fun readTextBlock(
        node: JsonNode,
        errorMessage: String = "Text block must be a mapping",
        allowNullText: Boolean = true,
        allowHints: Boolean = true,
    ): TextBlock {
        val blockMap =
            node as? JsonNode.ObjectNode
                ?: throw ValidationException(errorMessage)
        val blockKeys = if (allowHints) textBlockKeys else textBlockCoreKeys
        val blockContract = blockMap.contractWithRequiredKeys(blockKeys, setOf("id", "text"))
        val text =
            when (val textNode = blockMap.entries.getValue("text")) {
                is JsonNode.ScalarNode.StringNode -> {
                    textNode.value
                }

                JsonNode.ScalarNode.NullNode -> {
                    if (allowNullText) {
                        null
                    } else {
                        throw ValidationException("Text block text must be string")
                    }
                }

                else -> {
                    throw ValidationException("Key 'text' must be a string")
                }
            }
        val hints = if (allowHints) blockContract.optionalNode("hints")?.let { readHints(it) } else null
        return TextBlock(
            id = blockContract.requireString("id"),
            text = text,
            hints = hints,
        )
    }

    private fun readHints(node: JsonNode): TextBlockHints {
        val hintsMap =
            node as? JsonNode.ObjectNode
                ?: throw ValidationException("Key 'hints' must be a mapping")
        val hintsContract = hintsMap.contractWithRequiredKeys(hintsKeys, emptySet())
        val expectedContent =
            hintsContract.optionalNode("expectedContent")?.let { contentNode ->
                val textNode =
                    contentNode as? JsonNode.ScalarNode.StringNode
                        ?: throw ValidationException("Key 'expectedContent' must be a string")
                textNode.value
            }
        val override =
            hintsContract.optionalNode("override")?.let { severityNode ->
                val severityText =
                    (severityNode as? JsonNode.ScalarNode.StringNode)?.value
                        ?: throw ValidationException("Key 'override' must be a string")
                parseSeverity(severityText)
            }
        val inlineWithNext =
            hintsContract.optionalNode("inlineWithNext")?.let { inlineNode ->
                (inlineNode as? JsonNode.ScalarNode.BooleanNode)?.value
                    ?: throw ValidationException("Key 'inlineWithNext' must be a boolean")
            }
        return TextBlockHints(expectedContent, override, inlineWithNext)
    }

    private fun parseSeverity(value: String): TextOverrideSeverity =
        when (value) {
            "alert" -> TextOverrideSeverity.ALERT
            "warn" -> TextOverrideSeverity.WARN
            "once" -> TextOverrideSeverity.ONCE
            "ok" -> TextOverrideSeverity.OK
            else -> throw ValidationException("Unknown override severity '$value'")
        }

    private fun readLanguageBlocks(
        language: String,
        node: JsonNode,
    ): List<TextBlock> {
        val blocksNode =
            node as? JsonNode.ArrayNode
                ?: throw ValidationException("Texts for '$language' must be a list")
        return blocksNode.items.map { item -> readTextBlock(item) }
    }

    private fun JsonNode.ObjectNode.contractWithRequiredKeys(
        allowedKeys: Set<String>,
        requiredKeys: Set<String> = allowedKeys,
    ): JsonObjectContract {
        val contract = JsonObjectContract(this)
        contract.requireNoExtraKeys(allowedKeys)
        contract.requireKeys(requiredKeys)
        return contract
    }

    private companion object {
        private val baselineKeys = setOf("kind", "id", "version", "texts")
        private val profileKeys = setOf("kind", "id", "version", "parent", "overrides")
        private val overrideKeys = setOf("values", "texts")
        private val referenceKeys = setOf("id", "version", "hash")
        private val textOverrideKeys = setOf("replace", "append")
        private val textBlockCoreKeys = setOf("id", "text")
        private val textBlockKeys = setOf("id", "text", "hints")
        private val hintsKeys = setOf("expectedContent", "override", "inlineWithNext")
    }
}
