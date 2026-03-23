package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.canonical.ClearTermsCanonicalNodeRenderer
import straightway.mosa.legal.clearterms.parse.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.placeholder.ClearTermsPlaceholderParser
import straightway.mosa.legal.clearterms.placeholder.ClearTermsPlaceholderType
import straightway.mosa.legal.clearterms.render.ClearTermsBlockBackgroundResolver
import straightway.mosa.legal.clearterms.render.ClearTermsTimeTextRenderer
import straightway.mosa.legal.clearterms.render.ClearTermsValueRenderer
import straightway.mosa.legal.clearterms.validation.ClearTermsProfileValidator

class ClearTermsRenderer(
    private val parser: ClearTermsJsonParser,
    private val hashProvider: ClearTermsHashProvider,
    private val textEncoder: ClearTermsTextEncoder,
    private val canonicalizer: ClearTermsCanonicalNodeRenderer = ClearTermsCanonicalNodeRenderer(),
    private val placeholderParser: ClearTermsPlaceholderParser = ClearTermsPlaceholderParser(),
    private val reader: ClearTermsDocumentReader = ClearTermsDocumentReader(),
    private val localeProvider: ClearTermsLocaleProvider,
    private val profileValidator: ClearTermsProfileValidator = ClearTermsProfileValidator(placeholderParser),
    private val timeZoneDisplayConverter: ClearTermsTimeZoneDisplayConverter = ClearTermsTimeZoneDisplayConverter.NoOp,
    private val valueRenderer: ClearTermsValueRenderer =
        ClearTermsValueRenderer(
            placeholderParser,
            localeProvider,
            timeTextRenderer = ClearTermsTimeTextRenderer(timeZoneDisplayConverter = timeZoneDisplayConverter),
        ),
) {
    suspend fun renderLanguage(
        baselineJson: String,
        profileJsons: List<String>,
        languageTag: String,
    ): ClearTermsRenderedLanguage {
        requireProfiles(profileJsons)
        val baselineNode = parseObject(baselineJson)
        val baseline = readBaseline(baselineNode)
        val baselineHash = hashFor(baselineNode)
        val parsedProfiles = parseProfiles(profileJsons)
        return renderWithParsedProfiles(baseline, baselineHash, parsedProfiles, languageTag)
    }

    private fun renderWithParsedProfiles(
        baseline: ClearTermsBaseline,
        baselineHash: String,
        parsedProfiles: List<ParsedProfile>,
        languageTag: String,
    ): ClearTermsRenderedLanguage {
        val state = buildRenderState(baseline, baselineHash, parsedProfiles)
        profileValidator.validateFinalTextsResolved(state.textsByLanguage)
        val languageBlocks = selectLanguageBlocks(state, languageTag)
        val finalData = resolveFinalData(languageTag, languageBlocks, state.mergedRawValues)
        val renderedBlocks = renderBlocks(languageTag, languageBlocks, state, finalData)
        val text = renderText(renderedBlocks)
        ensureResolved(text)
        return ClearTermsRenderedLanguage(languageTag, text, renderedBlocks)
    }

    private suspend fun hashFor(node: ClearTermsJsonNode.ObjectNode): String {
        val canonical = canonicalizer.render(node)
        val digest = hashProvider.sha256(textEncoder.encode(canonical))
        return "sha256:${digest.toHexLower()}"
    }

    private fun requireProfiles(profileJsons: List<String>) {
        if (profileJsons.isEmpty()) {
            throw ClearTermsValidationException("At least one profile is required")
        }
    }

    private fun parseObject(json: String): ClearTermsJsonNode.ObjectNode = parser.parse(json)

    private fun readBaseline(node: ClearTermsJsonNode.ObjectNode): ClearTermsBaseline {
        val baseline = reader.readBaseline(node)
        profileValidator.validateBaselineStructure(baseline)
        return baseline
    }

    private suspend fun parseProfiles(profileJsons: List<String>): List<ParsedProfile> = profileJsons.map { parseProfile(it) }

    private suspend fun parseProfile(profileJson: String): ParsedProfile {
        val node = parseObject(profileJson)
        val profile = reader.readProfile(node)
        val hash = hashFor(node)
        return ParsedProfile(profile, hash)
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

    private data class RenderState(
        var parentId: String,
        var parentVersion: String,
        var parentHash: String,
        val mergedRawValues: MutableMap<String, ClearTermsJsonNode.ScalarNode>,
        var textsByLanguage: Map<String, List<ClearTermsTextBlock>>,
        val blockOverrideCount: MutableMap<String, Int>,
        val placeholderOverrideCount: MutableMap<String, Int>,
        val baselineBlocksById: Map<String, ClearTermsTextBlock>,
        val baselineBlockIds: Set<String>,
    )

    private data class FinalRenderData(
        val placeholders: Map<String, ClearTermsPlaceholderType>,
        val values: Map<String, ClearTermsValue>,
    )

    private fun buildRenderState(
        baseline: ClearTermsBaseline,
        baselineHash: String,
        parsedProfiles: List<ParsedProfile>,
    ): RenderState {
        val state = initialRenderState(baseline, baselineHash)
        parsedProfiles.forEach { applyProfile(state, it) }
        return state
    }

    private fun initialRenderState(
        baseline: ClearTermsBaseline,
        baselineHash: String,
    ): RenderState {
        val baselineBlocksById =
            baseline.textsByLanguage.values
                .first()
                .associateBy { it.id }
        return RenderState(
            parentId = baseline.id,
            parentVersion = baseline.version,
            parentHash = baselineHash,
            mergedRawValues = linkedMapOf(),
            textsByLanguage = baseline.textsByLanguage,
            blockOverrideCount = linkedMapOf(),
            placeholderOverrideCount = linkedMapOf(),
            baselineBlocksById = baselineBlocksById,
            baselineBlockIds = baselineBlocksById.keys,
        )
    }

    private fun applyProfile(
        state: RenderState,
        parsedProfile: ParsedProfile,
    ) {
        validateParentReference(state, parsedProfile)
        countOverrides(state, parsedProfile)
        mergeValues(state, parsedProfile)
        applyTextOverrides(state, parsedProfile)
        advanceParent(state, parsedProfile)
    }

    private fun validateParentReference(
        state: RenderState,
        parsedProfile: ParsedProfile,
    ) {
        profileValidator.validateReference(
            parsedProfile.profile.parent,
            state.parentId,
            state.parentVersion,
            state.parentHash,
        )
    }

    private fun countOverrides(
        state: RenderState,
        parsedProfile: ParsedProfile,
    ) {
        countBlockOverrides(parsedProfile.profile.overrides.texts, state.blockOverrideCount)
        countPlaceholderOverrides(parsedProfile.profile.overrides.values, state.placeholderOverrideCount)
    }

    private fun mergeValues(
        state: RenderState,
        parsedProfile: ParsedProfile,
    ) {
        state.mergedRawValues.putAll(parsedProfile.profile.overrides.values ?: emptyMap())
    }

    private fun applyTextOverrides(
        state: RenderState,
        parsedProfile: ParsedProfile,
    ) {
        state.textsByLanguage =
            profileValidator.applyTextOverrides(
                state.textsByLanguage,
                parsedProfile.profile.overrides.texts,
            )
    }

    private fun advanceParent(
        state: RenderState,
        parsedProfile: ParsedProfile,
    ) {
        state.parentId = parsedProfile.profile.id
        state.parentVersion = parsedProfile.profile.version
        state.parentHash = parsedProfile.hash
    }

    private fun selectLanguageBlocks(
        state: RenderState,
        languageTag: String,
    ): List<ClearTermsTextBlock> =
        state.textsByLanguage[languageTag]
            ?: throw ClearTermsValidationException("Language '$languageTag' not available")

    private fun resolveFinalData(
        languageTag: String,
        languageBlocks: List<ClearTermsTextBlock>,
        rawValues: Map<String, ClearTermsJsonNode.ScalarNode>,
    ): FinalRenderData {
        val textsByLanguage = mapOf(languageTag to languageBlocks)
        profileValidator.validateFinalTextsResolved(textsByLanguage)
        val placeholders = profileValidator.placeholdersByKey(textsByLanguage)
        val values =
            profileValidator.validateAndCoerceValues(
                rawValues,
                placeholders,
                requireAll = true,
            )
        return FinalRenderData(placeholders, values)
    }

    private fun renderBlocks(
        language: String,
        blocks: List<ClearTermsTextBlock>,
        state: RenderState,
        finalData: FinalRenderData,
    ): List<ClearTermsRenderedBlock> = blocks.map { renderBlock(language, it, state, finalData) }

    private fun renderBlock(
        language: String,
        block: ClearTermsTextBlock,
        state: RenderState,
        finalData: FinalRenderData,
    ): ClearTermsRenderedBlock {
        val background = resolveBlockBackground(block.id, state)
        val segments = renderSegments(language, block, background, state, finalData)
        return ClearTermsRenderedBlock(
            id = block.id,
            isBaselineOverride = hasBlockOverride(block.id, state.blockOverrideCount),
            inlineWithNext = block.hints?.inlineWithNext == true,
            segments = segments,
        )
    }

    private fun renderSegments(
        language: String,
        block: ClearTermsTextBlock,
        background: ClearTermsRenderBackground,
        state: RenderState,
        finalData: FinalRenderData,
    ): List<ClearTermsRenderedBlock.Segment> =
        if (background == ClearTermsRenderBackground.NONE) {
            renderSegmentDetails(language, block, state, finalData)
        } else {
            renderSingleBackgroundSegment(language, block, background, finalData.values)
        }

    private fun renderSegmentDetails(
        language: String,
        block: ClearTermsTextBlock,
        state: RenderState,
        finalData: FinalRenderData,
    ): List<ClearTermsRenderedBlock.Segment> =
        valueRenderer.renderBlockSegments(
            block = block,
            values = finalData.values,
            languageTag = language,
            placeholderBackgroundByKey =
                placeholderBackgroundByKey(
                    finalPlaceholders = finalData.placeholders,
                    placeholderOverrideCount = state.placeholderOverrideCount,
                ),
            placeholderIsBaselineOverrideByKey =
                placeholderIsBaselineOverrideByKey(
                    finalPlaceholders = finalData.placeholders,
                    placeholderOverrideCount = state.placeholderOverrideCount,
                ),
        )

    private fun renderSingleBackgroundSegment(
        language: String,
        block: ClearTermsTextBlock,
        background: ClearTermsRenderBackground,
        values: Map<String, ClearTermsValue>,
    ): List<ClearTermsRenderedBlock.Segment> {
        val text = renderPlainText(language, block, values)
        return listOf(ClearTermsRenderedBlock.Segment(text, background))
    }

    private fun renderPlainText(
        language: String,
        block: ClearTermsTextBlock,
        values: Map<String, ClearTermsValue>,
    ): String =
        valueRenderer
            .renderBlockSegments(
                block = block,
                values = values,
                languageTag = language,
                placeholderBackgroundByKey = emptyMap(),
                placeholderIsBaselineOverrideByKey = emptyMap(),
            ).joinToString("") { it.text }

    private fun renderText(blocks: List<ClearTermsRenderedBlock>): String = blocks.joinToString("") { renderBlockText(it) }

    private fun renderBlockText(block: ClearTermsRenderedBlock): String = block.segments.joinToString("") { it.text }

    private fun ensureResolved(text: String) {
        valueRenderer.ensureNoUnresolvedPlaceholders(text)
    }

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
        state: RenderState,
    ): ClearTermsRenderBackground =
        ClearTermsBlockBackgroundResolver.resolve(
            blockId = blockId,
            blockOverrideCount = state.blockOverrideCount,
            baselineBlocksById = state.baselineBlocksById,
            baselineBlockIds = state.baselineBlockIds,
        )

    private fun hasBlockOverride(
        blockId: String,
        blockOverrideCount: Map<String, Int>,
    ): Boolean = (blockOverrideCount[blockId] ?: 0) > 0

    private fun placeholderBackgroundByKey(
        finalPlaceholders: Map<String, ClearTermsPlaceholderType>,
        placeholderOverrideCount: Map<String, Int>,
    ): Map<String, ClearTermsRenderBackground> {
        val backgrounds = linkedMapOf<String, ClearTermsRenderBackground>()
        finalPlaceholders.keys.forEach { key ->
            backgrounds[key] = resolvePlaceholderBackground(key, placeholderOverrideCount)
        }
        return backgrounds
    }

    private fun resolvePlaceholderBackground(
        key: String,
        placeholderOverrideCount: Map<String, Int>,
    ): ClearTermsRenderBackground =
        if ((placeholderOverrideCount[key] ?: 0) > 1) {
            ClearTermsRenderBackground.YELLOW
        } else {
            ClearTermsRenderBackground.NONE
        }

    private fun placeholderIsBaselineOverrideByKey(
        finalPlaceholders: Map<String, ClearTermsPlaceholderType>,
        placeholderOverrideCount: Map<String, Int>,
    ): Map<String, Boolean> {
        val overrides = linkedMapOf<String, Boolean>()
        finalPlaceholders.keys.forEach { key ->
            overrides[key] = isPlaceholderOverride(key, placeholderOverrideCount)
        }
        return overrides
    }

    private fun isPlaceholderOverride(
        key: String,
        placeholderOverrideCount: Map<String, Int>,
    ): Boolean = (placeholderOverrideCount[key] ?: 0) > 0
}
