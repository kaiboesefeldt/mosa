package straightway.mosa.client.legal

import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.legal.clearterms.ClearTermsJsonNode
import straightway.mosa.legal.clearterms.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.ClearTermsLocaleProvider
import straightway.mosa.legal.clearterms.ClearTermsLocaleRepository
import straightway.mosa.legal.clearterms.ClearTermsPlaceholderParser
import straightway.mosa.legal.clearterms.ClearTermsProfile
import straightway.mosa.legal.clearterms.ClearTermsProfileValidator
import straightway.mosa.legal.clearterms.ClearTermsRenderBackground
import straightway.mosa.legal.clearterms.ClearTermsRenderedLanguage
import straightway.mosa.legal.clearterms.ClearTermsRenderedBlock
import straightway.mosa.legal.clearterms.ClearTermsTextBlock
import straightway.mosa.legal.clearterms.ClearTermsTextOverrideSeverity
import straightway.mosa.legal.clearterms.ClearTermsTimeTextRenderer
import straightway.mosa.legal.clearterms.ClearTermsTimeZoneDisplayConverter
import straightway.mosa.legal.clearterms.ClearTermsValueRenderer
import straightway.mosa.legal.clearterms.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.JsClearTermsResourceReader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class LegalPrivacyPolicyService(
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val preferredLanguageTagsProvider: () -> List<String>,
    private val clearTermsContentProvider: PrivacyClearTermsContentProvider,
    private val markdownToHtmlRenderer: MarkdownToHtmlRenderer = MarkdownToHtmlRenderer(),
    private val clearTermsLocaleProvider: ClearTermsLocaleProvider = ClearTermsLocaleRepository(JsClearTermsResourceReader()),
    private val timeZoneDisplayConverter: ClearTermsTimeZoneDisplayConverter = ClearTermsTimeZoneDisplayConverter.NoOp,
) {
    private val clearTermsJsonParser = ClearTermsJsonParser()
    private val clearTermsDocumentReader = ClearTermsDocumentReader()
    private val clearTermsPlaceholderParser = ClearTermsPlaceholderParser()
    private val clearTermsProfileValidator = ClearTermsProfileValidator(clearTermsPlaceholderParser)
    private val clearTermsValueRenderer =
        ClearTermsValueRenderer(
            clearTermsPlaceholderParser,
            clearTermsLocaleProvider,
            timeTextRenderer = ClearTermsTimeTextRenderer(timeZoneDisplayConverter = timeZoneDisplayConverter),
        )

    suspend fun loadPrivacyPolicyHtml(): String {
        return loadPrivacyPolicyDocument().html
    }

    suspend fun loadPrivacyPolicyDocument(): LegalPrivacyPolicyDocument {
        return loadDocument { it.privacyClearTerms() }
    }

    suspend fun loadTermsDocument(): LegalPrivacyPolicyDocument {
        return loadDocument { it.termsClearTerms() }
    }

    private suspend fun loadDocument(
        chainLoader: suspend (straightway.mosa.client.pod.PodAccess) -> straightway.mosa.legal.LegalDocumentChainResponse,
    ): LegalPrivacyPolicyDocument {
        val podBase = podBaseProvider.base() ?: throw IllegalStateException("No pod base available")
        val podAccess = podAccessFactory(podBase)
        val chain = chainLoader(podAccess)
        val baseline = chain.baseline.toCanonicalJson()
        val profiles = chain.profiles.map { profile -> profile.toCanonicalJson() }
        val languageTag = selectLanguageTag(availableLanguageTags(baseline, profiles), preferredLanguageTagsProvider())
        val renderResult = clearTermsContentProvider.renderedLanguage(baseline, profiles, languageTag)
        if (renderResult.blocks.isEmpty()) {
            return LegalPrivacyPolicyDocument(
                html = markdownToHtmlRenderer.render(renderResult.text),
                uncriticalTextByBlockId = emptyMap(),
            )
        }
        val uncriticalTextByBlockId =
            resolveUncriticalTextByBlockId(
                languageTag = languageTag,
                baselineJson = baseline,
                profileJsons = profiles,
                renderedBlocks = renderResult.blocks,
            )
        return LegalPrivacyPolicyDocument(
            html = renderBlocks(renderResult.blocks),
            uncriticalTextByBlockId = uncriticalTextByBlockId,
        )
    }

    private fun selectLanguageTag(
        languageTags: Set<String>,
        preferredLanguageTags: List<String>,
    ): String {
        check(languageTags.isNotEmpty()) { "No ClearTerms text available" }
        val candidates =
            buildList {
                preferredLanguageTags.forEach { tag ->
                    val normalized = normalizeLanguageTag(tag)
                    if (normalized.isNotBlank()) {
                        add(normalized)
                        val primary = normalized.substringBefore('-')
                        if (primary.isNotBlank()) {
                            add(primary)
                        }
                    }
                }
                add("en")
            }.distinct()

        candidates.forEach { candidate ->
            if (candidate in languageTags) {
                return candidate
            }
        }

        if ("en" in languageTags) {
            return "en"
        }

        return languageTags.minBy { it.lowercase() }
    }

    private fun normalizeLanguageTag(languageTag: String): String = languageTag.trim().lowercase()

    private fun JsonObject.toCanonicalJson(): String = this["canonicalJson"]?.jsonPrimitive?.content ?: toString()

    private fun availableLanguageTags(
        baselineJson: String,
        profileJsons: List<String>,
    ): Set<String> {
        val baseline = clearTermsDocumentReader.readBaseline(clearTermsJsonParser.parse(baselineJson))
        clearTermsProfileValidator.validateBaselineStructure(baseline)
        var textsByLanguage =
            baseline.textsByLanguage.entries.associate { (language, blocks) ->
                normalizeLanguageTag(language) to blocks
            }
        profileJsons.forEach { profileJson ->
            val profile = clearTermsDocumentReader.readProfile(clearTermsJsonParser.parse(profileJson))
            textsByLanguage = clearTermsProfileValidator.applyTextOverrides(textsByLanguage, profile.overrides.texts)
        }
        clearTermsProfileValidator.validateFinalTextsResolved(textsByLanguage)
        return textsByLanguage.keys
    }

    private fun renderBlock(
        block: ClearTermsRenderedBlock,
        groupId: String = block.id,
    ): String {
        val markdown = block.toMarkedMarkdown()
        val html = markdownToHtmlRenderer.render(markdown)
        val blockBackground = block.blockBackground()
        val dataAttributes = """data-ct-group-id="${escapeAttribute(groupId)}" data-ct-block-ids="${escapeAttribute(block.id)}""""
        return if (blockBackground == ClearTermsRenderBackground.NONE && !block.isBaselineOverride) {
            """<div class="legal-ct-block" $dataAttributes>$html</div>"""
        } else {
            val classes =
                buildList {
                    add("legal-ct-block")
                    if (blockBackground != ClearTermsRenderBackground.NONE) {
                        add(cssClassFor(blockBackground))
                    }
                    if (block.isBaselineOverride) {
                        add("legal-ct-baseline-diff")
                    }
                }.joinToString(" ")
            val style = inlineStyleFor(blockBackground)
            if (style.isEmpty()) {
                """<div class="$classes" $dataAttributes>$html</div>"""
            } else {
                """<div class="$classes" style="$style" $dataAttributes>$html</div>"""
            }
        }
    }

    private fun renderBlocks(blocks: List<ClearTermsRenderedBlock>): String {
        val html = StringBuilder()
        var index = 0
        while (index < blocks.size) {
            val current = blocks[index]
            val next = blocks.getOrNull(index + 1)
            if (current.inlineWithNext && next != null) {
                html.append(renderInlinePair(current, next))
                index += 2
            } else {
                html.append(renderBlock(current, current.id))
                index += 1
            }
        }
        return html.toString()
    }

    private fun renderInlinePair(
        current: ClearTermsRenderedBlock,
        next: ClearTermsRenderedBlock,
    ): String {
        val groupId = "${current.id}|${next.id}"
        val markdown =
            current.toMarkedMarkdown(includeBaselineOverrideMarker = true) +
                next.toMarkedMarkdown(includeBaselineOverrideMarker = true)
        val html = markdownToHtmlRenderer.render(markdown)
        val attributes =
            """data-ct-group-id="${escapeAttribute(groupId)}" data-ct-block-ids="${escapeAttribute(current.id)} ${escapeAttribute(next.id)}""""
        return """<div class="legal-ct-inline-group" $attributes>$html</div>"""
    }

    private fun ClearTermsRenderedBlock.toMarkedMarkdown(
        includeBaselineOverrideMarker: Boolean = false,
    ): String {
        val rendered =
            segments.joinToString("") { segment ->
                when {
                    segment.background != ClearTermsRenderBackground.NONE ->
                        "${openMarkerFor(segment.background, id)}${segment.text}${MARKER_CLOSE}"
                    segment.isBaselineOverride ->
                        "$MARKER_OPEN_BASELINE${segment.text}$MARKER_CLOSE"
                    else -> segment.text
                }
            }
        return if (includeBaselineOverrideMarker && isBaselineOverride) {
            "$MARKER_OPEN_BASELINE$rendered$MARKER_CLOSE"
        } else {
            rendered
        }
    }

    private suspend fun resolveUncriticalTextByBlockId(
        languageTag: String,
        baselineJson: String,
        profileJsons: List<String>,
        renderedBlocks: List<ClearTermsRenderedBlock>,
    ): Map<String, String> {
        val renderResultsByChainStep = linkedMapOf<Int, ClearTermsRenderedLanguage>()
        renderResultsByChainStep[profileJsons.size] = ClearTermsRenderedLanguage(languageTag = languageTag, text = "", blocks = renderedBlocks)
        for (profileCount in 1 until profileJsons.size) {
            runCatching {
                clearTermsContentProvider.renderedLanguage(baselineJson, profileJsons.take(profileCount), languageTag)
            }.onSuccess { partial ->
                renderResultsByChainStep[profileCount] = partial
            }
        }
        if (renderResultsByChainStep.size > 1) {
            return resolveUncriticalTextByBlockIdFromRenderResults(renderResultsByChainStep)
        }
        return try {
            resolveUncriticalTextByBlockIdFromChain(
                languageTag = languageTag,
                baselineJson = baselineJson,
                profileJsons = profileJsons,
                renderedBlocks = renderedBlocks,
            )
        } catch (_: Throwable) {
            resolveUncriticalTextByBlockIdFromRenderResults(renderResultsByChainStep)
        }
    }

    private fun resolveUncriticalTextByBlockIdFromRenderResults(renderResultsByChainStep: Map<Int, ClearTermsRenderedLanguage>): Map<String, String> {
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
                    ?.replace(unresolvedPlaceholderRegex, "...")
                    ?.ifBlank { "..." }
                    ?: ""
            map[blockId] = previousText
        }
        return map
    }

    private fun resolveUncriticalTextByBlockIdFromChain(
        languageTag: String,
        baselineJson: String,
        profileJsons: List<String>,
        renderedBlocks: List<ClearTermsRenderedBlock>,
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
        val profiles =
            profileJsons.map { profileJson ->
                clearTermsDocumentReader.readProfile(clearTermsJsonParser.parse(profileJson))
            }
        val normalizedLanguageTag = normalizeLanguageTag(languageTag)
        val chainSteps = buildChainSteps(baseline, profiles)
        val baselineBlocksById =
            baseline.textsByLanguage.values
                .first()
                .associateBy { block -> block.id }
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
            result[blockId] =
                previousStep
                    ?.renderBlockTextBestEffort(normalizedLanguageTag, blockId)
                    ?: ""
        }
        return result
    }

    private fun buildChainSteps(
        baseline: straightway.mosa.legal.clearterms.ClearTermsBaseline,
        profiles: List<ClearTermsProfile>,
    ): List<ChainStep> {
        val steps = mutableListOf<ChainStep>()
        var textsByLanguage =
            baseline.textsByLanguage.entries.associate { (language, blocks) ->
                normalizeLanguageTag(language) to blocks
            }
        val mergedValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        val blockOverrideCount = linkedMapOf<String, Int>()
        val placeholderOverrideCount = linkedMapOf<String, Int>()
        steps +=
            ChainStep(
                textsByLanguage = textsByLanguage,
                mergedValues = mergedValues.toMap(),
                blockOverrideCount = blockOverrideCount.toMap(),
                placeholderOverrideCount = placeholderOverrideCount.toMap(),
            )
        profiles.forEach { profile ->
            textsByLanguage = clearTermsProfileValidator.applyTextOverrides(textsByLanguage, profile.overrides.texts)
            profile.overrides.values.orEmpty().forEach { (key, value) ->
                mergedValues[key] = value
                placeholderOverrideCount[key] = (placeholderOverrideCount[key] ?: 0) + 1
            }
            countBlockOverrides(profile.overrides.texts, blockOverrideCount)
            steps +=
                ChainStep(
                    textsByLanguage = textsByLanguage,
                    mergedValues = mergedValues.toMap(),
                    blockOverrideCount = blockOverrideCount.toMap(),
                    placeholderOverrideCount = placeholderOverrideCount.toMap(),
                )
        }
        return steps
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
                    textOverrides.replace.keys + textOverrides.append.orEmpty().map { block -> block.id }
                }.toSet()
        keys.forEach { blockId ->
            blockOverrideCount[blockId] = (blockOverrideCount[blockId] ?: 0) + 1
        }
    }

    private fun ClearTermsRenderedBlock.isHighlighted(): Boolean =
        segments.any { segment ->
            segment.background == ClearTermsRenderBackground.YELLOW ||
                segment.background == ClearTermsRenderBackground.RED
        }

    private fun ChainStep.isBlockHighlighted(
        languageTag: String,
        blockId: String,
        baselineBlocksById: Map<String, ClearTermsTextBlock>,
        baselineBlockIds: Set<String>,
    ): Boolean {
        val block = textsByLanguage[languageTag].orEmpty().firstOrNull { candidate -> candidate.id == blockId } ?: return false
        val blockBackground =
            resolveBlockBackground(
                blockId = block.id,
                blockOverrideCount = blockOverrideCount,
                baselineBlocksById = baselineBlocksById,
                baselineBlockIds = baselineBlockIds,
            )
        if (blockBackground != ClearTermsRenderBackground.NONE) {
            return true
        }
        val text = block.text ?: return false
        val placeholders =
            clearTermsPlaceholderParser
                .parse(text)
                .filterIsInstance<ClearTermsPlaceholderParser.Segment.Placeholder>()
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
        return renderedText.replace(unresolvedPlaceholderRegex, "...").ifBlank { "..." }
    }

    private fun ClearTermsRenderedBlock.blockBackground(): ClearTermsRenderBackground {
        val first = segments.firstOrNull()?.background ?: ClearTermsRenderBackground.NONE
        if (first == ClearTermsRenderBackground.NONE) {
            return ClearTermsRenderBackground.NONE
        }
        return if (segments.all { it.background == first }) {
            first
        } else {
            ClearTermsRenderBackground.NONE
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
        return when (effectiveSeverity) {
            ClearTermsTextOverrideSeverity.ALERT -> ClearTermsRenderBackground.RED
            ClearTermsTextOverrideSeverity.WARN -> ClearTermsRenderBackground.YELLOW
            ClearTermsTextOverrideSeverity.ONCE ->
                if (count > 1) {
                    ClearTermsRenderBackground.RED
                } else {
                    ClearTermsRenderBackground.NONE
                }
            ClearTermsTextOverrideSeverity.OK -> ClearTermsRenderBackground.NONE
        }
    }

    private data class ChainStep(
        val textsByLanguage: Map<String, List<ClearTermsTextBlock>>,
        val mergedValues: Map<String, ClearTermsJsonNode.ScalarNode>,
        val blockOverrideCount: Map<String, Int>,
        val placeholderOverrideCount: Map<String, Int>,
    )

    private fun cssClassFor(background: ClearTermsRenderBackground): String =
        when (background) {
            ClearTermsRenderBackground.NONE -> "legal-ct-bg-none"
            ClearTermsRenderBackground.YELLOW -> "legal-ct-bg-yellow"
            ClearTermsRenderBackground.RED -> "legal-ct-bg-red"
        }

    private fun openMarkerFor(
        background: ClearTermsRenderBackground,
        blockId: String,
    ): String =
        when (background) {
            ClearTermsRenderBackground.NONE -> ""
            ClearTermsRenderBackground.YELLOW -> "$MARKER_OPEN_YELLOW:$blockId$MARKER_OPEN_SUFFIX"
            ClearTermsRenderBackground.RED -> "$MARKER_OPEN_RED:$blockId$MARKER_OPEN_SUFFIX"
        }

    private fun inlineStyleFor(background: ClearTermsRenderBackground): String =
        when (background) {
            ClearTermsRenderBackground.NONE -> ""
            ClearTermsRenderBackground.YELLOW -> INLINE_STYLE_YELLOW
            ClearTermsRenderBackground.RED -> INLINE_STYLE_RED
        }

    private fun escapeAttribute(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    companion object {
        const val MARKER_OPEN_BASELINE = "[[CTBG:DF]]"
        const val MARKER_OPEN_YELLOW = "[[CTBG:YL"
        const val MARKER_OPEN_RED = "[[CTBG:RD"
        const val MARKER_OPEN_SUFFIX = "]]"
        const val MARKER_CLOSE = "[[/CTBG]]"
        const val INLINE_STYLE_YELLOW = "background-color: #fef3c7; color: #78350f;"
        const val INLINE_STYLE_RED = "background-color: #fee2e2; color: #7f1d1d;"
        private val unresolvedPlaceholderRegex = Regex("\\{\\{[^{}]+\\}\\}")
    }
}
