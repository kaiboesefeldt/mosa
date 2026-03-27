package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.canonical.CanonicalNodeRenderer
import straightway.mosa.legal.clearterms.document.Baseline
import straightway.mosa.legal.clearterms.document.Profile
import straightway.mosa.legal.clearterms.document.TextBlock
import straightway.mosa.legal.clearterms.locale.LocaleProvider
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.legal.clearterms.parse.JsonNode
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.placeholder.PlaceholderType
import straightway.mosa.legal.clearterms.render.BlockBackgroundResolver
import straightway.mosa.legal.clearterms.render.RenderBackground
import straightway.mosa.legal.clearterms.render.RenderedBlock
import straightway.mosa.legal.clearterms.render.RenderedLanguage
import straightway.mosa.legal.clearterms.render.TimeTextRenderer
import straightway.mosa.legal.clearterms.render.TimeZoneDisplayConverter
import straightway.mosa.legal.clearterms.render.ValueRenderer
import straightway.mosa.legal.clearterms.spi.HashProvider
import straightway.mosa.legal.clearterms.spi.TextEncoder
import straightway.mosa.legal.clearterms.validation.ProfileValidator
import straightway.mosa.legal.clearterms.validation.ValidationException
import straightway.mosa.legal.clearterms.value.Value

class ClearTermsRenderer(
    private val parser: JsonParser,
    private val hashProvider: HashProvider,
    private val textEncoder: TextEncoder,
    private val canonicalizer: CanonicalNodeRenderer = CanonicalNodeRenderer(),
    private val placeholderParser: PlaceholderParser = PlaceholderParser(),
    private val reader: DocumentReader = DocumentReader(),
    private val localeProvider: LocaleProvider,
    private val profileValidator: ProfileValidator = ProfileValidator(placeholderParser),
    private val timeZoneDisplayConverter: TimeZoneDisplayConverter = TimeZoneDisplayConverter.NoOp,
    private val valueRenderer: ValueRenderer =
        ValueRenderer(
            placeholderParser,
            localeProvider,
            timeTextRenderer = TimeTextRenderer(timeZoneDisplayConverter = timeZoneDisplayConverter),
        ),
) {
    suspend fun renderLanguage(
        baselineJson: String,
        profileJsons: List<String>,
        languageTag: String,
    ): RenderedLanguage {
        requireProfiles(profileJsons)
        val baselineNode = parseObject(baselineJson)
        val baseline = readBaseline(baselineNode)
        val baselineHash = hashFor(baselineNode)
        val parsedProfiles = parseProfiles(profileJsons)
        return renderWithParsedProfiles(baseline, baselineHash, parsedProfiles, languageTag)
    }

    private fun renderWithParsedProfiles(
        baseline: Baseline,
        baselineHash: String,
        parsedProfiles: List<ParsedProfile>,
        languageTag: String,
    ): RenderedLanguage {
        val state = buildRenderState(baseline, baselineHash, parsedProfiles)
        profileValidator.validateFinalTextsResolved(state.textsByLanguage)
        val languageBlocks = selectLanguageBlocks(state, languageTag)
        val finalData = resolveFinalData(languageTag, languageBlocks, state.mergedRawValues)
        val renderedBlocks = renderBlocks(languageTag, languageBlocks, state, finalData)
        val text = renderText(renderedBlocks)
        ensureResolved(text)
        return RenderedLanguage(languageTag, text, renderedBlocks)
    }

    private suspend fun hashFor(node: JsonNode.ObjectNode): String {
        val canonical = canonicalizer.render(node)
        val digest = hashProvider.sha256(textEncoder.encode(canonical))
        return "sha256:${digest.toHexLower()}"
    }

    private fun requireProfiles(profileJsons: List<String>) {
        if (profileJsons.isEmpty()) {
            throw ValidationException("At least one profile is required")
        }
    }

    private fun parseObject(json: String): JsonNode.ObjectNode = parser.parse(json)

    private fun readBaseline(node: JsonNode.ObjectNode): Baseline {
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
        val profile: Profile,
        val hash: String,
    )

    private data class RenderState(
        var parentId: String,
        var parentVersion: String,
        var parentHash: String,
        val mergedRawValues: MutableMap<String, JsonNode.ScalarNode>,
        var textsByLanguage: Map<String, List<TextBlock>>,
        val blockOverrideCount: MutableMap<String, Int>,
        val placeholderOverrideCount: MutableMap<String, Int>,
        val baselineBlocksById: Map<String, TextBlock>,
        val baselineBlockIds: Set<String>,
    )

    private data class FinalRenderData(
        val placeholders: Map<String, PlaceholderType>,
        val values: Map<String, Value>,
    )

    private fun buildRenderState(
        baseline: Baseline,
        baselineHash: String,
        parsedProfiles: List<ParsedProfile>,
    ): RenderState {
        val state = initialRenderState(baseline, baselineHash)
        parsedProfiles.forEach { applyProfile(state, it) }
        return state
    }

    private fun initialRenderState(
        baseline: Baseline,
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
    ): List<TextBlock> =
        state.textsByLanguage[languageTag]
            ?: throw ValidationException("Language '$languageTag' not available")

    private fun resolveFinalData(
        languageTag: String,
        languageBlocks: List<TextBlock>,
        rawValues: Map<String, JsonNode.ScalarNode>,
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
        blocks: List<TextBlock>,
        state: RenderState,
        finalData: FinalRenderData,
    ): List<RenderedBlock> = blocks.map { renderBlock(language, it, state, finalData) }

    private fun renderBlock(
        language: String,
        block: TextBlock,
        state: RenderState,
        finalData: FinalRenderData,
    ): RenderedBlock {
        val background = resolveBlockBackground(block.id, state)
        val segments = renderSegments(language, block, background, state, finalData)
        return RenderedBlock(
            id = block.id,
            isBaselineOverride = hasBlockOverride(block.id, state.blockOverrideCount),
            inlineWithNext = block.hints?.inlineWithNext == true,
            segments = segments,
        )
    }

    private fun renderSegments(
        language: String,
        block: TextBlock,
        background: RenderBackground,
        state: RenderState,
        finalData: FinalRenderData,
    ): List<RenderedBlock.Segment> =
        if (background == RenderBackground.NONE) {
            renderSegmentDetails(language, block, state, finalData)
        } else {
            renderSingleBackgroundSegment(language, block, background, finalData.values)
        }

    private fun renderSegmentDetails(
        language: String,
        block: TextBlock,
        state: RenderState,
        finalData: FinalRenderData,
    ): List<RenderedBlock.Segment> =
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
        block: TextBlock,
        background: RenderBackground,
        values: Map<String, Value>,
    ): List<RenderedBlock.Segment> {
        val text = renderPlainText(language, block, values)
        return listOf(RenderedBlock.Segment(text, background))
    }

    private fun renderPlainText(
        language: String,
        block: TextBlock,
        values: Map<String, Value>,
    ): String =
        valueRenderer
            .renderBlockSegments(
                block = block,
                values = values,
                languageTag = language,
                placeholderBackgroundByKey = emptyMap(),
                placeholderIsBaselineOverrideByKey = emptyMap(),
            ).joinToString("") { it.text }

    private fun renderText(blocks: List<RenderedBlock>): String = blocks.joinToString("") { renderBlockText(it) }

    private fun renderBlockText(block: RenderedBlock): String = block.segments.joinToString("") { it.text }

    private fun ensureResolved(text: String) {
        valueRenderer.ensureNoUnresolvedPlaceholders(text)
    }

    private fun countBlockOverrides(
        overrides: Map<String, Profile.TextOverrides>?,
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
        values: Map<String, JsonNode.ScalarNode>?,
        placeholderOverrideCount: MutableMap<String, Int>,
    ) {
        values?.keys?.forEach { key ->
            placeholderOverrideCount[key] = (placeholderOverrideCount[key] ?: 0) + 1
        }
    }

    private fun resolveBlockBackground(
        blockId: String,
        state: RenderState,
    ): RenderBackground =
        BlockBackgroundResolver.resolve(
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
        finalPlaceholders: Map<String, PlaceholderType>,
        placeholderOverrideCount: Map<String, Int>,
    ): Map<String, RenderBackground> {
        val backgrounds = linkedMapOf<String, RenderBackground>()
        finalPlaceholders.keys.forEach { key ->
            backgrounds[key] = resolvePlaceholderBackground(key, placeholderOverrideCount)
        }
        return backgrounds
    }

    private fun resolvePlaceholderBackground(
        key: String,
        placeholderOverrideCount: Map<String, Int>,
    ): RenderBackground =
        if ((placeholderOverrideCount[key] ?: 0) > 1) {
            RenderBackground.YELLOW
        } else {
            RenderBackground.NONE
        }

    private fun placeholderIsBaselineOverrideByKey(
        finalPlaceholders: Map<String, PlaceholderType>,
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
