package straightway.mosa.client.legal

import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.legal.clearterms.parse.JsonNode
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.render.BlockBackgroundResolver
import straightway.mosa.legal.clearterms.locale.LocaleProvider
import straightway.mosa.legal.clearterms.locale.LocaleRepository
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.document.Profile
import straightway.mosa.legal.clearterms.validation.ProfileValidator
import straightway.mosa.legal.clearterms.render.RenderBackground
import straightway.mosa.legal.clearterms.render.RenderedLanguage
import straightway.mosa.legal.clearterms.render.RenderedBlock
import straightway.mosa.legal.clearterms.document.TextBlock
import straightway.mosa.legal.clearterms.render.TimeTextRenderer
import straightway.mosa.legal.clearterms.render.TimeZoneDisplayConverter
import straightway.mosa.legal.clearterms.render.ValueRenderer
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.client.legal.clearterms.browser.spi.BrowserResourceReader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class LegalPrivacyPolicyService(
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val preferredLanguageTagsProvider: () -> List<String>,
    private val clearTermsContentProvider: PrivacyClearTermsContentProvider,
    private val markdownToHtmlRenderer: MarkdownToHtmlRenderer = MarkdownToHtmlRenderer(),
    private val clearTermsLocaleProvider: LocaleProvider = LocaleRepository(BrowserResourceReader()),
    private val timeZoneDisplayConverter: TimeZoneDisplayConverter = TimeZoneDisplayConverter.NoOp,
) {
    private val clearTermsJsonParser = JsonParser()
    private val clearTermsDocumentReader = DocumentReader()
    private val clearTermsPlaceholderParser = PlaceholderParser()
    private val clearTermsProfileValidator = ProfileValidator(clearTermsPlaceholderParser)
    private val clearTermsValueRenderer =
        ValueRenderer(
            clearTermsPlaceholderParser,
            clearTermsLocaleProvider,
            timeTextRenderer = TimeTextRenderer(timeZoneDisplayConverter = timeZoneDisplayConverter),
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
        block: RenderedBlock,
        groupId: String = block.id,
    ): String {
        val markdown = block.toMarkedMarkdown()
        val html = markdownToHtmlRenderer.render(markdown)
        val blockBackground = block.blockBackground()
        val dataAttributes = """data-ct-group-id="${escapeAttribute(groupId)}" data-ct-block-ids="${escapeAttribute(block.id)}""""
        return if (blockBackground == RenderBackground.NONE && !block.isBaselineOverride) {
            """<div class="legal-ct-block" $dataAttributes>$html</div>"""
        } else {
            val classes =
                buildList {
                    add("legal-ct-block")
                    if (blockBackground != RenderBackground.NONE) {
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

    private fun renderBlocks(blocks: List<RenderedBlock>): String {
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
        current: RenderedBlock,
        next: RenderedBlock,
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

    private fun RenderedBlock.toMarkedMarkdown(
        includeBaselineOverrideMarker: Boolean = false,
    ): String {
        val rendered =
            segments.joinToString("") { segment ->
                when {
                    segment.background != RenderBackground.NONE ->
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

    private fun resolveUncriticalTextByBlockIdFromRenderResults(renderResultsByChainStep: Map<Int, RenderedLanguage>): Map<String, String> {
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
        baseline: straightway.mosa.legal.clearterms.document.Baseline,
        profiles: List<Profile>,
    ): List<ChainStep> {
        val steps = mutableListOf<ChainStep>()
        var textsByLanguage =
            baseline.textsByLanguage.entries.associate { (language, blocks) ->
                normalizeLanguageTag(language) to blocks
            }
        val mergedValues = linkedMapOf<String, JsonNode.ScalarNode>()
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
            segment.background == RenderBackground.YELLOW ||
                segment.background == RenderBackground.RED
        }

    private fun ChainStep.isBlockHighlighted(
        languageTag: String,
        blockId: String,
        baselineBlocksById: Map<String, TextBlock>,
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
        return renderedText.replace(unresolvedPlaceholderRegex, "...").ifBlank { "..." }
    }

    private fun RenderedBlock.blockBackground(): RenderBackground {
        val first = segments.firstOrNull()?.background ?: RenderBackground.NONE
        if (first == RenderBackground.NONE) {
            return RenderBackground.NONE
        }
        return if (segments.all { it.background == first }) {
            first
        } else {
            RenderBackground.NONE
        }
    }

    private fun resolveBlockBackground(
        blockId: String,
        blockOverrideCount: Map<String, Int>,
        baselineBlocksById: Map<String, TextBlock>,
        baselineBlockIds: Set<String>,
    ): RenderBackground =
        BlockBackgroundResolver.resolve(
            blockId = blockId,
            blockOverrideCount = blockOverrideCount,
            baselineBlocksById = baselineBlocksById,
            baselineBlockIds = baselineBlockIds,
        )

    private data class ChainStep(
        val textsByLanguage: Map<String, List<TextBlock>>,
        val mergedValues: Map<String, JsonNode.ScalarNode>,
        val blockOverrideCount: Map<String, Int>,
        val placeholderOverrideCount: Map<String, Int>,
    )

    private fun cssClassFor(background: RenderBackground): String =
        when (background) {
            RenderBackground.NONE -> "legal-ct-bg-none"
            RenderBackground.YELLOW -> "legal-ct-bg-yellow"
            RenderBackground.RED -> "legal-ct-bg-red"
        }

    private fun openMarkerFor(
        background: RenderBackground,
        blockId: String,
    ): String =
        when (background) {
            RenderBackground.NONE -> ""
            RenderBackground.YELLOW -> "$MARKER_OPEN_YELLOW:$blockId$MARKER_OPEN_SUFFIX"
            RenderBackground.RED -> "$MARKER_OPEN_RED:$blockId$MARKER_OPEN_SUFFIX"
        }

    private fun inlineStyleFor(background: RenderBackground): String =
        when (background) {
            RenderBackground.NONE -> ""
            RenderBackground.YELLOW -> INLINE_STYLE_YELLOW
            RenderBackground.RED -> INLINE_STYLE_RED
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
