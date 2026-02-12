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
        defaultJson: String,
        deviationJson: String,
    ): ClearTermsRenderResult {
        val baselineNode = parser.parse(baselineJson)
        val defaultNode = parser.parse(defaultJson)
        val deviationNode = parser.parse(deviationJson)

        val baselineHash = hashFor(baselineNode)
        val defaultHash = hashFor(defaultNode)

        val baseline = reader.readBaseline(baselineNode)
        val defaultProfile = reader.readDefault(defaultNode)
        val deviationProfile = reader.readDeviation(deviationNode)

        profileValidator.validateReference(defaultProfile.baseline, baseline.id, baseline.version, baselineHash)
        profileValidator.validateReference(deviationProfile.default, defaultProfile.id, defaultProfile.version, defaultHash)

        profileValidator.validateBaselineStructure(baseline)

        val baselinePlaceholders = profileValidator.placeholdersByKey(baseline.textsByLanguage)

        val defaultValues = profileValidator.validateAndCoerceValues(defaultProfile.values, baselinePlaceholders, requireAll = true)

        val effectiveTexts = profileValidator.applyTextOverrides(baseline, deviationProfile.overrides.texts)
        val effectivePlaceholders = profileValidator.placeholdersByKey(effectiveTexts)

        val deviationValues =
            profileValidator.validateAndCoerceValues(deviationProfile.overrides.values, effectivePlaceholders, requireAll = false)

        val effectiveValues = defaultValues + deviationValues

        val renderedByLanguage =
            effectiveTexts.mapValues { (language, blocks) ->
                valueRenderer.renderBlocks(blocks, effectiveValues, language)
            }

        return ClearTermsRenderResult(renderedByLanguage)
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
}
