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

        parsedProfiles.forEach { parsed ->
            profileValidator.validateReference(parsed.profile.parent, parentId, parentVersion, parentHash)
            mergedRawValues.putAll(parsed.profile.overrides.values ?: emptyMap())
            intermediateTexts = profileValidator.applyTextOverrides(intermediateTexts, parsed.profile.overrides.texts)
            parentId = parsed.profile.id
            parentVersion = parsed.profile.version
            parentHash = parsed.hash
        }

        profileValidator.validateFinalTextsResolved(intermediateTexts)
        val finalPlaceholders = profileValidator.placeholdersByKey(intermediateTexts)
        val finalValues = profileValidator.validateAndCoerceValues(mergedRawValues, finalPlaceholders, requireAll = true)
        val renderedByLanguage =
            intermediateTexts.mapValues { (language, blocks) ->
                valueRenderer.renderBlocks(blocks, finalValues, language)
            }

        return ClearTermsRenderResult(renderedByLanguage)
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
}
