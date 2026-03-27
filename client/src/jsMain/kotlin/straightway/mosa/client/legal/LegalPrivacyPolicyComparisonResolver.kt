package straightway.mosa.client.legal

import straightway.mosa.legal.clearterms.document.Profile
import straightway.mosa.legal.clearterms.document.TextBlock
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.legal.clearterms.parse.JsonNode
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.render.BlockBackgroundResolver
import straightway.mosa.legal.clearterms.render.RenderBackground
import straightway.mosa.legal.clearterms.render.RenderedBlock
import straightway.mosa.legal.clearterms.render.RenderedLanguage
import straightway.mosa.legal.clearterms.render.ValueRenderer
import straightway.mosa.legal.clearterms.validation.ProfileValidator

internal class LegalPrivacyPolicyComparisonResolver(
    private val clearTermsContentProvider: PrivacyClearTermsContentProvider,
    private val clearTermsJsonParser: JsonParser = JsonParser(),
    private val clearTermsDocumentReader: DocumentReader = DocumentReader(),
    private val clearTermsPlaceholderParser: PlaceholderParser = PlaceholderParser(),
    private val clearTermsProfileValidator: ProfileValidator = ProfileValidator(clearTermsPlaceholderParser),
    private val clearTermsValueRenderer: ValueRenderer,
) {
    suspend fun resolve(
        languageTag: String,
        baselineJson: String,
        profileJsons: List<String>,
        renderedBlocks: List<RenderedBlock>,
    ): Map<String, String> {
        val renderResultsByChainStep = linkedMapOf<Int, RenderedLanguage>()
        renderResultsByChainStep[profileJsons.size] = RenderedLanguage(languageTag = languageTag, text = "", blocks = renderedBlocks)
        for (profileCount in 1 until profileJsons.size) {
            runCatching {
                clearTermsContentProvider.renderedLanguage(baselineJson, profileJsons.take(profileCount), languageTag)
            }.onSuccess { partial ->
                renderResultsByChainStep[profileCount] = partial
            }
        }
        if (renderResultsByChainStep.size > 1) {
            return resolveFromRenderResults(renderResultsByChainStep)
        }
        return try {
            resolveFromChain(languageTag, baselineJson, profileJsons, renderedBlocks)
        } catch (_: Throwable) {
            resolveFromRenderResults(renderResultsByChainStep)
        }
    }

    private fun resolveFromRenderResults(renderResultsByChainStep: Map<Int, RenderedLanguage>): Map<String, String> {
        if (renderResultsByChainStep.isEmpty()) {
            return emptyMap()
        }
        val stepsByLanguage =
            renderResultsByChainStep
                .toList()
                .sortedBy { (step, _) -> step }
                .map { (_, stepResult) -> stepResult.blocks }
        val finalBlocks = stepsByLanguage.last()
        val highlightedIds =
            finalBlocks
                .filter { block -> block.isHighlighted() }
                .map { block -> block.id }
                .toSet()
        if (highlightedIds.isEmpty()) {
            return emptyMap()
        }
        val map = linkedMapOf<String, String>()
        highlightedIds.forEach { blockId ->
            val firstHighlightedIndex =
                stepsByLanguage.indexOfFirst { blocks ->
                    blocks.any { block ->
                        block.id == blockId && block.isHighlighted()
                    }
                }
            val previousBlocks =
                if (firstHighlightedIndex > 0) {
                    stepsByLanguage[firstHighlightedIndex - 1]
                } else {
                    emptyList()
                }
            val previousText =
                previousBlocks
                    .firstOrNull { block -> block.id == blockId }
                    ?.segments
                    ?.joinToString("") { segment -> segment.text }
                    ?.replace(UNRESOLVED_PLACEHOLDER_REGEX, "...")
                    ?.ifBlank { "..." }
                    ?: ""
            map[blockId] = previousText
        }
        return map
    }

    private fun resolveFromChain(
        languageTag: String,
        baselineJson: String,
        profileJsons: List<String>,
        renderedBlocks: List<RenderedBlock>,
    ): Map<String, String> {
        if (renderedBlocks.isEmpty()) {
            return emptyMap()
        }
        val highlightedIds =
            renderedBlocks
                .filter { block -> block.isHighlighted() }
                .map { block -> block.id }
                .toSet()
        if (highlightedIds.isEmpty()) {
            return emptyMap()
        }
        val baseline = clearTermsDocumentReader.readBaseline(clearTermsJsonParser.parse(baselineJson))
        clearTermsProfileValidator.validateBaselineStructure(baseline)
        val profiles = profileJsons.map { profileJson -> clearTermsDocumentReader.readProfile(clearTermsJsonParser.parse(profileJson)) }
        val normalizedLanguageTag = languageTag.trim().lowercase()
        val chainSteps = buildChainSteps(baseline, profiles)
        val baselineBlocksById = baseline.textsByLanguage.values.first().associateBy { block -> block.id }
        val baselineBlockIds = baselineBlocksById.keys
        val result = linkedMapOf<String, String>()
        highlightedIds.forEach { blockId ->
            val firstHighlightedIndex =
                chainSteps.indexOfFirst { step ->
                    step.isBlockHighlighted(
                        languageTag = normalizedLanguageTag,
                        blockId = blockId,
                        baselineBlocksById = baselineBlocksById,
                        baselineBlockIds = baselineBlockIds,
                    )
                }
            val previousStep = chainSteps.getOrNull(firstHighlightedIndex - 1)
            result[blockId] = previousStep?.renderBlockTextBestEffort(normalizedLanguageTag, blockId) ?: ""
        }
        return result
    }

    private fun buildChainSteps(
        baseline: straightway.mosa.legal.clearterms.document.Baseline,
        profiles: List<Profile>,
    ): List<ChainStep> {
        val steps = mutableListOf<ChainStep>()
        var textsByLanguage =
            baseline.textsByLanguage.entries.associate { (language, blocks) ->
                language.trim().lowercase() to blocks
            }
        val mergedValues = linkedMapOf<String, JsonNode.ScalarNode>()
        val blockOverrideCount = linkedMapOf<String, Int>()
        val placeholderOverrideCount = linkedMapOf<String, Int>()
        steps += ChainStep(textsByLanguage, mergedValues.toMap(), blockOverrideCount.toMap(), placeholderOverrideCount.toMap())
        profiles.forEach { profile ->
            textsByLanguage = clearTermsProfileValidator.applyTextOverrides(textsByLanguage, profile.overrides.texts)
            profile.overrides.values.orEmpty().forEach { (key, value) ->
                mergedValues[key] = value
                placeholderOverrideCount[key] = (placeholderOverrideCount[key] ?: 0) + 1
            }
            countBlockOverrides(profile.overrides.texts, blockOverrideCount)
            steps += ChainStep(textsByLanguage, mergedValues.toMap(), blockOverrideCount.toMap(), placeholderOverrideCount.toMap())
        }
        return steps
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
                    textOverrides.replace.keys + textOverrides.append.orEmpty().map { block -> block.id }
                }.toSet()
        keys.forEach { blockId ->
            blockOverrideCount[blockId] = (blockOverrideCount[blockId] ?: 0) + 1
        }
    }

    private fun RenderedBlock.isHighlighted(): Boolean =
        segments.any { segment ->
            segment.background == RenderBackground.YELLOW || segment.background == RenderBackground.RED
        }

    private fun ChainStep.isBlockHighlighted(
        languageTag: String,
        blockId: String,
        baselineBlocksById: Map<String, TextBlock>,
        baselineBlockIds: Set<String>,
    ): Boolean {
        val block = textsByLanguage[languageTag].orEmpty().firstOrNull { candidate -> candidate.id == blockId } ?: return false
        val blockBackground =
            BlockBackgroundResolver.resolve(
                blockId = block.id,
                blockOverrideCount = blockOverrideCount,
                baselineBlocksById = baselineBlocksById,
                baselineBlockIds = baselineBlockIds,
            )
        if (blockBackground != RenderBackground.NONE) {
            return true
        }
        val text = block.text ?: return false
        val placeholders =
            clearTermsPlaceholderParser
                .parse(text)
                .filterIsInstance<PlaceholderParser.Segment.Placeholder>()
        return placeholders.any { placeholder ->
            (placeholderOverrideCount[placeholder.placeholder.key] ?: 0) > 1
        }
    }

    private fun ChainStep.renderBlockTextBestEffort(
        languageTag: String,
        blockId: String,
    ): String {
        val block = textsByLanguage[languageTag].orEmpty().firstOrNull { candidate -> candidate.id == blockId } ?: return ""
        val renderedText =
            runCatching {
                val placeholderTypes = clearTermsProfileValidator.placeholdersByKey(textsByLanguage)
                val values =
                    clearTermsProfileValidator.validateAndCoerceValues(
                        rawValues = mergedValues,
                        types = placeholderTypes,
                        requireAll = false,
                    )
                clearTermsValueRenderer
                    .renderBlockSegments(
                        block = block,
                        values = values,
                        languageTag = languageTag,
                        placeholderBackgroundByKey = emptyMap(),
                        placeholderIsBaselineOverrideByKey = emptyMap(),
                    ).joinToString("") { segment -> segment.text }
            }.getOrElse {
                block.text ?: "..."
            }
        return renderedText.replace(UNRESOLVED_PLACEHOLDER_REGEX, "...").ifBlank { "..." }
    }

    private data class ChainStep(
        val textsByLanguage: Map<String, List<TextBlock>>,
        val mergedValues: Map<String, JsonNode.ScalarNode>,
        val blockOverrideCount: Map<String, Int>,
        val placeholderOverrideCount: Map<String, Int>,
    )

    private companion object {
        private val UNRESOLVED_PLACEHOLDER_REGEX = Regex("\\{\\{[^{}]+\\}\\}")
    }
}
