package straightway.mosa.client.legal

import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.legal.clearterms.ClearTermsRenderBackground
import straightway.mosa.legal.clearterms.ClearTermsRenderedBlock

class LegalPrivacyPolicyService(
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val preferredLanguageTagsProvider: () -> List<String>,
    private val clearTermsContentProvider: PrivacyClearTermsContentProvider,
    private val markdownToHtmlRenderer: MarkdownToHtmlRenderer = MarkdownToHtmlRenderer(),
) {
    suspend fun loadPrivacyPolicyHtml(): String {
        val podBase = podBaseProvider.base() ?: throw IllegalStateException("No pod base available")
        val podAccess = podAccessFactory(podBase)
        val baseline = podAccess.privacyBaselineClearTerms()
        val profiles =
            listOf(
                podAccess.privacyDefaultClearTerms(),
                podAccess.privacyMosaClearTerms(),
                podAccess.privacyOperatorClearTerms(),
            ).distinct()
        val renderResult = clearTermsContentProvider.renderedTextsByLanguage(baseline, profiles)
        val normalizedTexts =
            renderResult.textsByLanguage.entries.associateBy(
                keySelector = { entry -> normalizeLanguageTag(entry.key) },
                valueTransform = { entry -> entry.value },
            )
        val normalizedBlocks =
            renderResult.blocksByLanguage.entries.associateBy(
                keySelector = { entry -> normalizeLanguageTag(entry.key) },
                valueTransform = { entry -> entry.value },
            )
        val languageTag = selectLanguageTag(normalizedTexts, preferredLanguageTagsProvider())
        val renderedBlocks = normalizedBlocks[languageTag]
        if (renderedBlocks == null) {
            val markdown = normalizedTexts.getValue(languageTag)
            return markdownToHtmlRenderer.render(markdown)
        }
        return renderBlocks(renderedBlocks)
    }

    private fun selectLanguageTag(
        textsByLanguage: Map<String, String>,
        preferredLanguageTags: List<String>,
    ): String {
        check(textsByLanguage.isNotEmpty()) { "No ClearTerms text available" }

        val normalizedTexts = textsByLanguage.entries.associateBy({ normalizeLanguageTag(it.key) }, { it.value })
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
            if (normalizedTexts[candidate] != null) {
                return candidate
            }
        }

        if (normalizedTexts["en"] != null) {
            return "en"
        }

        return normalizeLanguageTag(textsByLanguage.keys.minBy { it.lowercase() })
    }

    private fun normalizeLanguageTag(languageTag: String): String = languageTag.trim().lowercase()

    private fun renderBlock(block: ClearTermsRenderedBlock): String {
        val markdown = block.toMarkedMarkdown()
        val html = markdownToHtmlRenderer.render(markdown)
        val blockBackground = block.blockBackground()
        return if (blockBackground == ClearTermsRenderBackground.NONE && !block.isBaselineOverride) {
            html
        } else {
            val classes =
                buildList {
                    if (blockBackground != ClearTermsRenderBackground.NONE) {
                        add(cssClassFor(blockBackground))
                    }
                    if (block.isBaselineOverride) {
                        add("legal-ct-baseline-diff")
                    }
                }.joinToString(" ")
            val style = inlineStyleFor(blockBackground)
            if (style.isEmpty()) {
                """<div class="$classes">$html</div>"""
            } else {
                """<div class="$classes" style="$style">$html</div>"""
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
                html.append(renderBlock(current))
                index += 1
            }
        }
        return html.toString()
    }

    private fun renderInlinePair(
        current: ClearTermsRenderedBlock,
        next: ClearTermsRenderedBlock,
    ): String {
        val markdown =
            current.toMarkedMarkdown(includeBaselineOverrideMarker = true) +
                next.toMarkedMarkdown(includeBaselineOverrideMarker = true)
        return markdownToHtmlRenderer.render(markdown)
    }

    private fun ClearTermsRenderedBlock.toMarkedMarkdown(includeBaselineOverrideMarker: Boolean = false): String {
        val rendered =
            segments.joinToString("") { segment ->
                when {
                    segment.background != ClearTermsRenderBackground.NONE ->
                        "${openMarkerFor(segment.background)}${segment.text}${MARKER_CLOSE}"
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

    private fun cssClassFor(background: ClearTermsRenderBackground): String =
        when (background) {
            ClearTermsRenderBackground.NONE -> "legal-ct-bg-none"
            ClearTermsRenderBackground.YELLOW -> "legal-ct-bg-yellow"
            ClearTermsRenderBackground.RED -> "legal-ct-bg-red"
        }

    private fun openMarkerFor(background: ClearTermsRenderBackground): String =
        when (background) {
            ClearTermsRenderBackground.NONE -> ""
            ClearTermsRenderBackground.YELLOW -> MARKER_OPEN_YELLOW
            ClearTermsRenderBackground.RED -> MARKER_OPEN_RED
        }

    private fun inlineStyleFor(background: ClearTermsRenderBackground): String =
        when (background) {
            ClearTermsRenderBackground.NONE -> ""
            ClearTermsRenderBackground.YELLOW -> INLINE_STYLE_YELLOW
            ClearTermsRenderBackground.RED -> INLINE_STYLE_RED
        }

    companion object {
        const val MARKER_OPEN_BASELINE = "[[CTBG:DF]]"
        const val MARKER_OPEN_YELLOW = "[[CTBG:YL]]"
        const val MARKER_OPEN_RED = "[[CTBG:RD]]"
        const val MARKER_CLOSE = "[[/CTBG]]"
        const val INLINE_STYLE_YELLOW = "background-color: #fef3c7; color: #78350f;"
        const val INLINE_STYLE_RED = "background-color: #fee2e2; color: #7f1d1d;"
    }
}
