package straightway.mosa.legal.clearterms

class ClearTermsDocumentReader {
    fun readBaseline(root: ClearTermsJsonNode.ObjectNode): ClearTermsBaseline {
        val contract = root.contractWithRequiredKeys(baselineKeys)
        requireDocumentKind(contract, "baseline", "Invalid baseline kind")
        val id = contract.requireString("id")
        val version = contract.requireString("version")
        val textsNode = contract.requireMap("texts")
        if (textsNode.entries.isEmpty()) {
            throw ClearTermsValidationException("Baseline must define at least one language")
        }
        val textsByLanguage = textsNode.entries.mapValues { (language, node) -> readLanguageBlocks(language, node) }
        return ClearTermsBaseline(id, version, textsByLanguage)
    }

    fun readDefault(root: ClearTermsJsonNode.ObjectNode): ClearTermsDefaultProfile {
        val contract = root.contractWithRequiredKeys(defaultKeys)
        requireDocumentKind(contract, "default", "Invalid default kind")
        val id = contract.requireString("id")
        val version = contract.requireString("version")
        val baselineReference = readReference(contract.requireMap("baseline"))
        val values = readScalarMap(contract.requireMap("values"), "Values must be scalar")
        return ClearTermsDefaultProfile(id, version, baselineReference, values)
    }

    fun readDeviation(root: ClearTermsJsonNode.ObjectNode): ClearTermsDeviationProfile {
        val contract = root.contractWithRequiredKeys(deviationKeys)
        requireDocumentKind(contract, "deviation", "Invalid deviation kind")
        val id = contract.requireString("id")
        val version = contract.requireString("version")
        val defaultReference = readReference(contract.requireMap("default"))

        val overridesContract = contract.requireMap("overrides").contractWithRequiredKeys(overrideKeys, setOf("values"))

        val values = readScalarMap(overridesContract.requireMap("values"), "Override values must be scalar")
        val texts = overridesContract.optionalNode("texts")?.let { parseDeviationTextOverrides(it) }

        return ClearTermsDeviationProfile(id, version, defaultReference, ClearTermsDeviationProfile.Overrides(values, texts))
    }

    fun readProfile(root: ClearTermsJsonNode.ObjectNode): ClearTermsProfile {
        val contract = root.contractWithRequiredKeys(profileKeys)
        requireDocumentKind(contract, "profile", "Invalid profile kind")
        val id = contract.requireString("id")
        val version = contract.requireString("version")
        val parentReference = readReference(contract.requireMap("parent"))

        val overridesContract = contract.requireMap("overrides").contractWithRequiredKeys(overrideKeys, setOf("values"))
        val values = readScalarMap(overridesContract.requireMap("values"), "Override values must be scalar")
        val texts = overridesContract.optionalNode("texts")?.let { parseProfileTextOverrides(it) }

        return ClearTermsProfile(id, version, parentReference, ClearTermsProfile.Overrides(values, texts))
    }

    private fun requireDocumentKind(
        contract: ClearTermsJsonObjectContract,
        expected: String,
        errorMessage: String,
    ) {
        if (contract.requireString("kind") != expected) {
            throw ClearTermsValidationException(errorMessage)
        }
    }

    private fun readReference(node: ClearTermsJsonNode.ObjectNode): ClearTermsReference {
        val contract = node.contractWithRequiredKeys(referenceKeys)
        return ClearTermsReference(
            id = contract.requireString("id"),
            version = contract.requireString("version"),
            hash = contract.requireString("hash"),
        )
    }

    private fun readScalarMap(
        node: ClearTermsJsonNode.ObjectNode,
        errorMessage: String,
    ): Map<String, ClearTermsJsonNode.ScalarNode> =
        node.entries.mapValues { (_, value) ->
            value as? ClearTermsJsonNode.ScalarNode
                ?: throw ClearTermsValidationException(errorMessage)
        }

    private fun parseDeviationTextOverrides(node: ClearTermsJsonNode): Map<String, ClearTermsDeviationProfile.TextOverrides> {
        val map =
            node as? ClearTermsJsonNode.ObjectNode
                ?: throw ClearTermsValidationException("Overrides texts must be a mapping")

        return map.entries.mapValues { (language, entryNode) ->
            val languageMap =
                entryNode as? ClearTermsJsonNode.ObjectNode
                    ?: throw ClearTermsValidationException("Overrides for '$language' must be a mapping")
            val languageContract = languageMap.contractWithRequiredKeys(textOverrideKeys)

            val replace =
                languageContract.requireMap("replace").entries.mapValues { (_, replaceValue) ->
                    val textNode =
                        replaceValue as? ClearTermsJsonNode.ScalarNode.StringNode
                            ?: throw ClearTermsValidationException("Replace text must be string")
                    textNode.value
                }

            val append =
                languageContract
                    .requireSequence(
                        "append",
                    ).items
                    .map { item -> readTextBlock(item, "Append text must be a mapping", allowNullText = false) }

            ClearTermsDeviationProfile.TextOverrides(replace, append)
        }
    }

    private fun parseProfileTextOverrides(node: ClearTermsJsonNode): Map<String, ClearTermsProfile.TextOverrides> {
        val map =
            node as? ClearTermsJsonNode.ObjectNode
                ?: throw ClearTermsValidationException("Overrides texts must be a mapping")

        return map.entries.mapValues { (language, entryNode) ->
            val languageMap =
                entryNode as? ClearTermsJsonNode.ObjectNode
                    ?: throw ClearTermsValidationException("Overrides for '$language' must be a mapping")
            val languageContract = languageMap.contractWithRequiredKeys(textOverrideKeys)

            val replace =
                languageContract.requireMap("replace").entries.mapValues { (_, replaceValue) ->
                    val textNode =
                        replaceValue as? ClearTermsJsonNode.ScalarNode.StringNode
                            ?: throw ClearTermsValidationException("Replace text must be string")
                    textNode.value
                }

            val append =
                languageContract
                    .requireSequence(
                        "append",
                    ).items
                    .map { item -> readTextBlock(item, "Append text must be a mapping", allowNullText = false) }

            ClearTermsProfile.TextOverrides(replace, append)
        }
    }

    private fun readTextBlock(
        node: ClearTermsJsonNode,
        errorMessage: String = "Text block must be a mapping",
        allowNullText: Boolean = true,
    ): ClearTermsTextBlock {
        val blockMap =
            node as? ClearTermsJsonNode.ObjectNode
                ?: throw ClearTermsValidationException(errorMessage)
        val blockContract = blockMap.contractWithRequiredKeys(textBlockKeys)
        val textNode = blockMap.entries.getValue("text")
        val text =
            when (textNode) {
                is ClearTermsJsonNode.ScalarNode.StringNode -> textNode.value
                ClearTermsJsonNode.ScalarNode.NullNode ->
                    if (allowNullText) {
                        null
                    } else {
                        throw ClearTermsValidationException("Text block text must be string")
                    }
                else -> throw ClearTermsValidationException("Key 'text' must be a string")
            }
        return ClearTermsTextBlock(
            id = blockContract.requireString("id"),
            text = text,
        )
    }

    private fun readLanguageBlocks(
        language: String,
        node: ClearTermsJsonNode,
    ): List<ClearTermsTextBlock> {
        val blocksNode =
            node as? ClearTermsJsonNode.ArrayNode
                ?: throw ClearTermsValidationException("Texts for '$language' must be a list")
        return blocksNode.items.map { item -> readTextBlock(item) }
    }

    private fun ClearTermsJsonNode.ObjectNode.contractWithRequiredKeys(
        allowedKeys: Set<String>,
        requiredKeys: Set<String> = allowedKeys,
    ): ClearTermsJsonObjectContract {
        val contract = ClearTermsJsonObjectContract(this)
        contract.requireNoExtraKeys(allowedKeys)
        contract.requireKeys(requiredKeys)
        return contract
    }

    private companion object {
        private val baselineKeys = setOf("kind", "id", "version", "texts")
        private val defaultKeys = setOf("kind", "id", "version", "baseline", "values")
        private val deviationKeys = setOf("kind", "id", "version", "default", "overrides")
        private val profileKeys = setOf("kind", "id", "version", "parent", "overrides")
        private val overrideKeys = setOf("values", "texts")
        private val referenceKeys = setOf("id", "version", "hash")
        private val textOverrideKeys = setOf("replace", "append")
        private val textBlockKeys = setOf("id", "text")
    }
}
