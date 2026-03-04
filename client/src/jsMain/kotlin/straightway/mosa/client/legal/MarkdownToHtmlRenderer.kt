package straightway.mosa.client.legal

class MarkdownToHtmlRenderer {
    fun render(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val html = StringBuilder()
        val paragraphLines = mutableListOf<String>()
        var inList = false

        fun closeParagraph() {
            if (paragraphLines.isEmpty()) return
            html.append("<p>")
            html.append(escape(paragraphLines.joinToString(" ")))
            html.append("</p>")
            paragraphLines.clear()
        }

        fun closeList() {
            if (!inList) return
            html.append("</ul>")
            inList = false
        }

        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                closeParagraph()
                closeList()
                return@forEach
            }

            val headingLevel = headingLevel(line)
            if (headingLevel != null) {
                closeParagraph()
                closeList()
                val text = line.drop(headingLevel + 1).trim()
                html.append("<h").append(headingLevel).append('>')
                html.append(escape(text))
                html.append("</h").append(headingLevel).append('>')
                return@forEach
            }

            val listItem = listItemContent(line)
            if (listItem != null) {
                closeParagraph()
                if (!inList) {
                    html.append("<ul>")
                    inList = true
                }
                html.append("<li>")
                html.append(escape(listItem))
                html.append("</li>")
                return@forEach
            }

            closeList()
            paragraphLines += line.trim()
        }

        closeParagraph()
        closeList()
        return html.toString()
    }

    private fun headingLevel(line: String): Int? {
        var level = 0
        while (level < line.length && line[level] == '#') {
            level += 1
        }
        if (level == 0 || level > 6) return null
        if (line.length <= level || line[level] != ' ') return null
        return level
    }

    private fun listItemContent(line: String): String? {
        val trimmed = line.trimStart()
        if (!(trimmed.startsWith("- ") || trimmed.startsWith("* "))) return null
        return trimmed.drop(2).trim()
    }

    private fun escape(text: String): String =
        buildString {
            var index = 0
            while (index < text.length) {
                when {
                    text.startsWith(LegalPrivacyPolicyService.MARKER_OPEN_BASELINE, index) -> {
                        append("""<span class="legal-ct-baseline-diff">""")
                        index += LegalPrivacyPolicyService.MARKER_OPEN_BASELINE.length
                    }

                    text.startsWith(LegalPrivacyPolicyService.MARKER_OPEN_YELLOW, index) -> {
                        val marker = parseMarker(text, index, LegalPrivacyPolicyService.MARKER_OPEN_YELLOW)
                        val blockIdAttribute = marker.blockId?.let { blockId -> """ data-ct-block-id="${escapeAttribute(blockId)}"""" } ?: ""
                        append(
                            """<span$blockIdAttribute class="legal-ct-bg-yellow legal-ct-baseline-diff" style="${LegalPrivacyPolicyService.INLINE_STYLE_YELLOW}">""",
                        )
                        index += marker.length
                    }

                    text.startsWith(LegalPrivacyPolicyService.MARKER_OPEN_RED, index) -> {
                        val marker = parseMarker(text, index, LegalPrivacyPolicyService.MARKER_OPEN_RED)
                        val blockIdAttribute = marker.blockId?.let { blockId -> """ data-ct-block-id="${escapeAttribute(blockId)}"""" } ?: ""
                        append(
                            """<span$blockIdAttribute class="legal-ct-bg-red legal-ct-baseline-diff" style="${LegalPrivacyPolicyService.INLINE_STYLE_RED}">""",
                        )
                        index += marker.length
                    }

                    text.startsWith(LegalPrivacyPolicyService.MARKER_CLOSE, index) -> {
                        append("</span>")
                        index += LegalPrivacyPolicyService.MARKER_CLOSE.length
                    }

                    else -> {
                        when (val char = text[index]) {
                            '&' -> append("&amp;")
                            '<' -> append("&lt;")
                            '>' -> append("&gt;")
                            '"' -> append("&quot;")
                            '\'' -> append("&#39;")
                            else -> append(char)
                        }
                        index += 1
                    }
                }
            }
        }

    private fun escapeAttribute(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun parseMarker(
        text: String,
        startIndex: Int,
        prefix: String,
    ): ParsedMarker {
        val markerEnd = text.indexOf(LegalPrivacyPolicyService.MARKER_OPEN_SUFFIX, startIndex + prefix.length)
        if (markerEnd < 0) {
            return ParsedMarker(length = prefix.length, blockId = null)
        }
        val payload = text.substring(startIndex + prefix.length, markerEnd)
        val blockId = payload.removePrefix(":").takeIf { it.isNotBlank() }
        return ParsedMarker(
            length = markerEnd + LegalPrivacyPolicyService.MARKER_OPEN_SUFFIX.length - startIndex,
            blockId = blockId,
        )
    }

    private data class ParsedMarker(
        val length: Int,
        val blockId: String?,
    )
}
