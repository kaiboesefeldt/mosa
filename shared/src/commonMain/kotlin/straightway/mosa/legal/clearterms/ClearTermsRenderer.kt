package straightway.mosa.legal.clearterms

class ClearTermsRenderer(
    private val parser: ClearTermsJsonParser,
    private val hashProvider: ClearTermsHashProvider,
    private val textEncoder: ClearTermsTextEncoder,
    private val canonicalizer: ClearTermsCanonicalJson = ClearTermsCanonicalJson(),
    private val placeholderParser: ClearTermsPlaceholderParser = ClearTermsPlaceholderParser(),
    private val reader: ClearTermsDocumentReader = ClearTermsDocumentReader(),
    private val localeProvider: ClearTermsLocaleProvider,
    private val profileValidator: ClearTermsProfileValidator = ClearTermsProfileValidator(placeholderParser),
    private val valueRenderer: ClearTermsValueRenderer = ClearTermsValueRenderer(placeholderParser, localeProvider),
) {
    suspend fun renderAll(
        baselineJson: String,
        profileJsons: List<String>,
    ): ClearTermsRenderResult {
        if (profileJsons.isEmpty()) {
            throw ClearTermsValidationException("At least one profile is required")
        }
        val baselineNode = parser.parse(baselineJson)
        val baselineHash = hashFor(baselineNode)
        val baseline = reader.readBaseline(baselineNode)
        profileValidator.validateBaselineStructure(baseline)

        val profileNodes = profileJsons.map { parser.parse(it) }
        val parsedProfiles = mutableListOf<ParsedProfile>()
        profileNodes.forEach { node ->
            parsedProfiles += ParsedProfile(profile = reader.readProfile(node), hash = hashFor(node))
        }

        return renderWithParsedProfiles(baseline, baselineHash, parsedProfiles)
    }

    suspend fun renderAll(
        baselineJson: String,
        defaultJson: String,
        deviationJson: String,
    ): ClearTermsRenderResult {
        val baselineNode = parser.parse(baselineJson)
        val defaultNode = parser.parse(defaultJson)
        val deviationNode = parser.parse(deviationJson)

        val baselineHash = hashFor(baselineNode)
        val defaultHash = hashFor(defaultNode)
        val deviationHash = hashFor(deviationNode)

        val baseline = reader.readBaseline(baselineNode)
        val defaultProfile = reader.readDefault(defaultNode)
        val deviationProfile = reader.readDeviation(deviationNode)
        profileValidator.validateBaselineStructure(baseline)

        val parsedProfiles =
            listOf(
                ParsedProfile(
                    profile =
                        ClearTermsProfile(
                            id = defaultProfile.id,
                            version = defaultProfile.version,
                            parent = defaultProfile.baseline,
                            overrides = ClearTermsProfile.Overrides(defaultProfile.values, null),
                        ),
                    hash = defaultHash,
                ),
                ParsedProfile(
                    profile =
                        ClearTermsProfile(
                            id = deviationProfile.id,
                            version = deviationProfile.version,
                            parent = deviationProfile.default,
                            overrides =
                                ClearTermsProfile.Overrides(
                                    values = deviationProfile.overrides.values,
                                    texts =
                                        deviationProfile.overrides.texts?.mapValues { (_, value) ->
                                            ClearTermsProfile.TextOverrides(value.replace, value.append)
                                        },
                                ),
                        ),
                    hash = deviationHash,
                ),
            )

        return renderWithParsedProfiles(baseline, baselineHash, parsedProfiles)
    }

    suspend fun renderLanguage(
        baselineJson: String,
        profileJsons: List<String>,
        languageTag: String,
    ): String {
        val result = renderAll(baselineJson, profileJsons)
        return result.textsByLanguage[languageTag]
            ?: throw ClearTermsValidationException("Language '$languageTag' not available")
    }

    suspend fun renderLanguage(
        baselineJson: String,
        defaultJson: String,
        deviationJson: String,
        languageTag: String,
    ): String {
        val result = renderAll(baselineJson, defaultJson, deviationJson)
        return result.textsByLanguage[languageTag]
            ?: throw ClearTermsValidationException("Language '$languageTag' not available")
    }

    private fun renderWithParsedProfiles(
        baseline: ClearTermsBaseline,
        baselineHash: String,
        parsedProfiles: List<ParsedProfile>,
    ): ClearTermsRenderResult {
        var parentId = baseline.id
        var parentVersion = baseline.version
        var parentHash = baselineHash

        val mergedRawValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        var intermediateTexts = baseline.textsByLanguage
        val blockOverrideCount = linkedMapOf<String, Int>()
        val placeholderOverrideCount = linkedMapOf<String, Int>()
        val baselineBlocksById =
            baseline.textsByLanguage.values
                .first()
                .associateBy { it.id }
        val baselineBlockIds = baselineBlocksById.keys

        parsedProfiles.forEach { parsed ->
            profileValidator.validateReference(parsed.profile.parent, parentId, parentVersion, parentHash)
            countBlockOverrides(parsed.profile.overrides.texts, blockOverrideCount)
            countPlaceholderOverrides(parsed.profile.overrides.values, placeholderOverrideCount)
            mergedRawValues.putAll(parsed.profile.overrides.values ?: emptyMap())
            intermediateTexts = profileValidator.applyTextOverrides(intermediateTexts, parsed.profile.overrides.texts)
            parentId = parsed.profile.id
            parentVersion = parsed.profile.version
            parentHash = parsed.hash
        }

        profileValidator.validateFinalTextsResolved(intermediateTexts)
        val finalPlaceholders = profileValidator.placeholdersByKey(intermediateTexts)
        val finalValues = profileValidator.validateAndCoerceValues(mergedRawValues, finalPlaceholders, requireAll = true)
        val renderedBlocksByLanguage =
            intermediateTexts.mapValues { (language, blocks) ->
                blocks.map { block ->
                    val blockBackground =
                        resolveBlockBackground(
                            blockId = block.id,
                            blockOverrideCount = blockOverrideCount,
                            baselineBlocksById = baselineBlocksById,
                            baselineBlockIds = baselineBlockIds,
                        )
                    val segments =
                        if (blockBackground == ClearTermsRenderBackground.NONE) {
                            valueRenderer.renderBlockSegments(
                                block = block,
                                values = finalValues,
                                languageTag = language,
                                placeholderBackgroundByKey =
                                    placeholderBackgroundByKey(
                                        finalPlaceholders = finalPlaceholders,
                                        placeholderOverrideCount = placeholderOverrideCount,
                                    ),
                            )
                        } else {
                            val rendered = valueRenderer.renderBlockSegments(block, finalValues, language, emptyMap())
                            val text = rendered.joinToString("") { segment -> segment.text }
                            listOf(ClearTermsRenderedBlock.Segment(text, blockBackground))
                        }
                    ClearTermsRenderedBlock(
                        id = block.id,
                        segments = segments,
                    )
                }
            }
        val renderedByLanguage =
            renderedBlocksByLanguage.mapValues { (_, blocks) ->
                blocks.joinToString("") { block ->
                    block.segments.joinToString("") { segment -> segment.text }
                }
            }
        renderedByLanguage.values.forEach { rendered -> valueRenderer.ensureNoUnresolvedPlaceholders(rendered) }

        return ClearTermsRenderResult(renderedByLanguage, renderedBlocksByLanguage)
    }

    private suspend fun hashFor(node: ClearTermsJsonNode.ObjectNode): String {
        val canonical = canonicalizer.canonicalize(node)
        val digest = hashProvider.sha256(textEncoder.encode(canonical))
        return "sha256:${digest.toHexLower()}"
    }

    private fun ByteArray.toHexLower(): String =
        joinToString("") { byte ->
            val value = byte.toInt() and 0xff
            value.toString(16).padStart(2, '0')
        }

    private data class ParsedProfile(
        val profile: ClearTermsProfile,
        val hash: String,
    )

    private fun countBlockOverrides(
        overrides: Map<String, ClearTermsProfile.TextOverrides>?,
        blockOverrideCount: MutableMap<String, Int>,
    ) {
        if (overrides == null) {
            return
        }
        val keys =
            overrides.values
                .flatMap { textOverrides ->
                    val replaced = textOverrides.replace.keys
                    val appended = textOverrides.append?.map { it.id } ?: emptyList()
                    replaced + appended
                }.toSet()
        keys.forEach { blockId ->
            blockOverrideCount[blockId] = (blockOverrideCount[blockId] ?: 0) + 1
        }
    }

    private fun countPlaceholderOverrides(
        values: Map<String, ClearTermsJsonNode.ScalarNode>?,
        placeholderOverrideCount: MutableMap<String, Int>,
    ) {
        values?.keys?.forEach { key ->
            placeholderOverrideCount[key] = (placeholderOverrideCount[key] ?: 0) + 1
        }
    }

    private fun resolveBlockBackground(
        blockId: String,
        blockOverrideCount: Map<String, Int>,
        baselineBlocksById: Map<String, ClearTermsTextBlock>,
        baselineBlockIds: Set<String>,
    ): ClearTermsRenderBackground {
        val count = blockOverrideCount[blockId] ?: 0
        if (blockId !in baselineBlockIds && count > 0) {
            return ClearTermsRenderBackground.RED
        }
        if (count == 0) {
            return ClearTermsRenderBackground.NONE
        }
        val baselineBlock = baselineBlocksById[blockId] ?: return ClearTermsRenderBackground.NONE
        val effectiveSeverity =
            baselineBlock.hints?.override
                ?: if (baselineBlock.text == null) {
                    ClearTermsTextOverrideSeverity.ONCE
                } else {
                    ClearTermsTextOverrideSeverity.ALERT
                }
        when (effectiveSeverity) {
            ClearTermsTextOverrideSeverity.ALERT -> return ClearTermsRenderBackground.RED
            ClearTermsTextOverrideSeverity.WARN -> return ClearTermsRenderBackground.YELLOW
            ClearTermsTextOverrideSeverity.ONCE ->
                if (count > 1) {
                    return ClearTermsRenderBackground.RED
                }
            ClearTermsTextOverrideSeverity.OK -> return ClearTermsRenderBackground.NONE
        }
        return ClearTermsRenderBackground.NONE
    }

    private fun placeholderBackgroundByKey(
        finalPlaceholders: Map<String, ClearTermsPlaceholderType>,
        placeholderOverrideCount: Map<String, Int>,
    ): Map<String, ClearTermsRenderBackground> {
        val backgrounds = linkedMapOf<String, ClearTermsRenderBackground>()
        finalPlaceholders.keys.forEach { key ->
            val overrideCount = placeholderOverrideCount[key] ?: 0
            backgrounds[key] =
                when {
                    overrideCount > 1 -> ClearTermsRenderBackground.YELLOW
                    else -> ClearTermsRenderBackground.NONE
                }
        }
        return backgrounds
    }
}
