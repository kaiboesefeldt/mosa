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
        return renderedBlocks.joinToString("") { block -> renderBlock(block) }
    }

    private fun selectLanguageTag(
        textsByLanguage: Map<String, String>,
        preferredLanguageTags: List<String>,
    ): String {
        if (textsByLanguage.isEmpty()) {
            throw IllegalStateException("No ClearTerms text available")
        }

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
        return if (blockBackground == ClearTermsRenderBackground.NONE) {
            html
        } else {
            """<div class="${cssClassFor(blockBackground)}">$html</div>"""
        }
    }

    private fun ClearTermsRenderedBlock.toMarkedMarkdown(): String =
        segments.joinToString("") { segment ->
            if (segment.background == ClearTermsRenderBackground.NONE) {
                segment.text
            } else {
                "${openMarkerFor(segment.background)}${segment.text}${MARKER_CLOSE}"
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
            ClearTermsRenderBackground.LIGHT_GREEN -> "legal-ct-bg-light-green"
            ClearTermsRenderBackground.YELLOW -> "legal-ct-bg-yellow"
            ClearTermsRenderBackground.RED -> "legal-ct-bg-red"
        }

    private fun openMarkerFor(background: ClearTermsRenderBackground): String =
        when (background) {
            ClearTermsRenderBackground.NONE -> ""
            ClearTermsRenderBackground.LIGHT_GREEN -> MARKER_OPEN_LIGHT_GREEN
            ClearTermsRenderBackground.YELLOW -> MARKER_OPEN_YELLOW
            ClearTermsRenderBackground.RED -> MARKER_OPEN_RED
        }

    companion object {
        const val MARKER_OPEN_LIGHT_GREEN = "[[CTBG:LG]]"
        const val MARKER_OPEN_YELLOW = "[[CTBG:YL]]"
        const val MARKER_OPEN_RED = "[[CTBG:RD]]"
        const val MARKER_CLOSE = "[[/CTBG]]"
    }
}
