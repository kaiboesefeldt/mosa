package straightway.mosa.client.legal

import straightway.mosa.legal.clearterms.render.RenderBackground
import straightway.mosa.legal.clearterms.render.RenderedBlock

internal class LegalPrivacyPolicyHtmlRenderer(
    private val markdownToHtmlRenderer: MarkdownToHtmlRenderer = MarkdownToHtmlRenderer(),
) {
    fun render(blocks: List<RenderedBlock>): String {
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
                        "${openMarkerFor(segment.background, id)}${segment.text}${LegalPrivacyPolicyService.MARKER_CLOSE}"
                    segment.isBaselineOverride ->
                        "${LegalPrivacyPolicyService.MARKER_OPEN_BASELINE}${segment.text}${LegalPrivacyPolicyService.MARKER_CLOSE}"
                    else -> segment.text
                }
            }
        return if (includeBaselineOverrideMarker && isBaselineOverride) {
            "${LegalPrivacyPolicyService.MARKER_OPEN_BASELINE}$rendered${LegalPrivacyPolicyService.MARKER_CLOSE}"
        } else {
            rendered
        }
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
            RenderBackground.YELLOW -> "${LegalPrivacyPolicyService.MARKER_OPEN_YELLOW}:$blockId${LegalPrivacyPolicyService.MARKER_OPEN_SUFFIX}"
            RenderBackground.RED -> "${LegalPrivacyPolicyService.MARKER_OPEN_RED}:$blockId${LegalPrivacyPolicyService.MARKER_OPEN_SUFFIX}"
        }

    private fun inlineStyleFor(background: RenderBackground): String =
        when (background) {
            RenderBackground.NONE -> ""
            RenderBackground.YELLOW -> LegalPrivacyPolicyService.INLINE_STYLE_YELLOW
            RenderBackground.RED -> LegalPrivacyPolicyService.INLINE_STYLE_RED
        }

    private fun escapeAttribute(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
