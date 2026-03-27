package straightway.mosa.legal.clearterms.render

import straightway.mosa.legal.clearterms.document.TextBlock
import straightway.mosa.legal.clearterms.locale.LocaleProvider
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.placeholder.PlaceholderType
import straightway.mosa.legal.clearterms.render.RenderBackground
import straightway.mosa.legal.clearterms.render.RenderedBlock
import straightway.mosa.legal.clearterms.validation.ValidationException
import straightway.mosa.legal.clearterms.value.Value
import straightway.mosa.legal.clearterms.placeholder.Placeholder as ParsedPlaceholder

class ValueRenderer(
    private val placeholderParser: PlaceholderParser,
    private val localeProvider: LocaleProvider,
    private val numberRenderer: NumberRenderer = NumberRenderer(),
    private val durationTextRenderer: DurationTextRenderer = DurationTextRenderer(),
    private val timeTextRenderer: TimeTextRenderer = TimeTextRenderer(),
) {
    fun renderBlocks(
        blocks: List<TextBlock>,
        values: Map<String, Value>,
        languageTag: String,
    ): String {
        val rendered =
            blocks.joinToString("") { block ->
                val text = block.text ?: throw ValidationException("Unresolved text block '${block.id}'")
                renderText(text, values, languageTag)
            }
        if (containsUnresolvedPlaceholder(rendered)) {
            throw ValidationException("Rendered text contains unresolved placeholders")
        }
        return rendered
    }

    private fun renderText(
        text: String,
        values: Map<String, Value>,
        languageTag: String,
    ): String {
        val segments = placeholderParser.parse(text)
        val result = StringBuilder()
        segments.forEach { segment ->
            when (segment) {
                is PlaceholderParser.Segment.Literal -> {
                    result.append(segment.text)
                }

                is PlaceholderParser.Segment.Placeholder -> {
                    result.append(
                        renderPlaceholder(segment.placeholder, values, languageTag),
                    )
                }
            }
        }
        return result.toString()
    }

    fun renderBlockSegments(
        block: TextBlock,
        values: Map<String, Value>,
        languageTag: String,
        placeholderBackgroundByKey: Map<String, RenderBackground>,
        placeholderIsBaselineOverrideByKey: Map<String, Boolean>,
    ): List<RenderedBlock.Segment> {
        val text = block.text ?: throw ValidationException("Unresolved text block '${block.id}'")
        val segments = placeholderParser.parse(text)
        val renderedSegments = mutableListOf<RenderedBlock.Segment>()
        segments.forEach { segment ->
            when (segment) {
                is PlaceholderParser.Segment.Literal -> {
                    if (segment.text.isNotEmpty()) {
                        renderedSegments += RenderedBlock.Segment(segment.text, RenderBackground.NONE)
                    }
                }

                is PlaceholderParser.Segment.Placeholder -> {
                    val rendered = renderPlaceholder(segment.placeholder, values, languageTag)
                    if (rendered.isNotEmpty()) {
                        val background = placeholderBackgroundByKey[segment.placeholder.key] ?: RenderBackground.NONE
                        val isBaselineOverride = placeholderIsBaselineOverrideByKey[segment.placeholder.key] == true
                        renderedSegments += RenderedBlock.Segment(rendered, background, isBaselineOverride)
                    }
                }
            }
        }
        return mergeAdjacentSegments(renderedSegments)
    }

    private fun renderPlaceholder(
        placeholder: ParsedPlaceholder,
        values: Map<String, Value>,
        languageTag: String,
    ): String {
        val value =
            values[placeholder.key]
                ?: throw ValidationException("Missing value for '${placeholder.key}'")
        return renderValue(value, placeholder, languageTag)
    }

    private fun renderValue(
        value: Value,
        placeholder: ParsedPlaceholder,
        languageTag: String,
    ): String {
        val raw =
            value.value ?: return placeholder.fallback
                ?: throw ValidationException("Missing fallback for '${placeholder.key}'")
        val locale = localeProvider.resolve(languageTag)
        return when (value.type) {
            PlaceholderType.STRING -> raw as String
            PlaceholderType.NUMBER -> numberRenderer.format(raw as Double, placeholder.numberOptions, locale)
            PlaceholderType.DURATION -> durationTextRenderer.renderDurationValue(raw as String, locale)
            PlaceholderType.TIME -> timeTextRenderer.renderTimeValue(raw as String, locale)
            PlaceholderType.WEEKDAY -> timeTextRenderer.renderWeekday(raw as String, locale)
        }
    }

    private fun containsUnresolvedPlaceholder(text: String): Boolean {
        var index = 0
        while (index < text.length - 1) {
            if (text.startsWith("{{", index)) {
                val endIndex = text.indexOf("}}", index + 2)
                if (endIndex < 0) {
                    throw ValidationException("Rendered text contains unterminated placeholder")
                }
                val content = text.substring(index + 2, endIndex)
                if (matchesPlaceholderSyntax(content)) {
                    return true
                }
                index = endIndex + 2
            } else {
                index += 1
            }
        }
        return false
    }

    fun ensureNoUnresolvedPlaceholders(text: String) {
        if (containsUnresolvedPlaceholder(text)) {
            throw ValidationException("Rendered text contains unresolved placeholders")
        }
    }

    private fun matchesPlaceholderSyntax(content: String): Boolean {
        val parts = content.split("??", limit = 2)
        val main = parts[0]
        val typeAndRest = main.split(":", limit = 2)
        if (typeAndRest.size != 2) {
            return false
        }
        if (!isSupportedPlaceholderType(typeAndRest[0])) {
            return false
        }
        val keyAndOptions = typeAndRest[1]
        val key = keyAndOptions.substringBefore("?")
        if (key.isBlank() || key.any { it.isWhitespace() }) {
            return false
        }
        if (keyAndOptions.contains("?")) {
            val options = keyAndOptions.substringAfter("?")
            if (options.isBlank()) {
                return false
            }
        }
        return true
    }

    private fun isSupportedPlaceholderType(value: String): Boolean =
        value == "string" || value == "number" || value == "duration" || value == "time" || value == "weekday"

    private fun mergeAdjacentSegments(segments: List<RenderedBlock.Segment>): List<RenderedBlock.Segment> {
        if (segments.isEmpty()) {
            return emptyList()
        }
        val merged = mutableListOf<RenderedBlock.Segment>()
        segments.forEach { segment ->
            val last = merged.lastOrNull()
            if (last != null && last.background == segment.background && last.isBaselineOverride == segment.isBaselineOverride) {
                merged[merged.lastIndex] =
                    RenderedBlock.Segment(
                        text = last.text + segment.text,
                        background = last.background,
                        isBaselineOverride = last.isBaselineOverride,
                    )
            } else {
                merged += segment
            }
        }
        return merged
    }
}
