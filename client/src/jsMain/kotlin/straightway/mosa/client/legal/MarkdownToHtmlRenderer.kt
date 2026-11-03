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
            html.append(renderInline(paragraphLines.joinToString(" ")))
            html.append("</p>")
            paragraphLines.clear()
        }

        fun closeList() {
            if (!inList) return
            html.append("</ul>")
            inList = false
        }

        var index = 0
        while (index < lines.size) {
            val line = lines[index].trimEnd()

            if (line.startsWith("```")) {
                closeParagraph()
                closeList()
                index++
                val codeLines = mutableListOf<String>()
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    codeLines += lines[index]
                    index++
                }
                if (index < lines.size) index++
                html.append("<pre><code>")
                html.append(escapeCode(codeLines.joinToString("\n")))
                html.append("</code></pre>")
                continue
            }

            if (isTableStart(lines, index)) {
                closeParagraph()
                closeList()
                html.append("<table><thead><tr>")
                tableCells(line).forEach { cell ->
                    html.append("<th>").append(escape(cell)).append("</th>")
                }
                html.append("</tr></thead><tbody>")
                index += 2
                while (index < lines.size && lines[index].trim().startsWith("|")) {
                    html.append("<tr>")
                    tableCells(lines[index]).forEach { cell ->
                        html.append("<td>").append(escape(cell)).append("</td>")
                    }
                    html.append("</tr>")
                    index++
                }
                html.append("</tbody></table>")
                continue
            }

            if (line.isBlank()) {
                closeParagraph()
                closeList()
                index++
                continue
            }

            val headingLevel = headingLevel(line)
            if (headingLevel != null) {
                closeParagraph()
                closeList()
                val text = line.drop(headingLevel + 1).trim()
                html.append("<h").append(headingLevel).append('>')
                html.append(renderInline(text))
                html.append("</h").append(headingLevel).append('>')
                index++
                continue
            }

            val listItem = listItemContent(line)
            if (listItem != null) {
                closeParagraph()
                if (!inList) {
                    html.append("<ul>")
                    inList = true
                }
                html.append("<li>")
                html.append(renderInline(listItem))
                html.append("</li>")
                index++
                continue
            }

            closeList()
            paragraphLines += line.trim()
            index++
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

    private fun isTableStart(
        lines: List<String>,
        index: Int,
    ): Boolean =
        index + 1 < lines.size &&
            lines[index].trim().startsWith("|") &&
            lines[index + 1].trim().startsWith("|") &&
            lines[index + 1].trim().removePrefix("|").removeSuffix("|").split('|').all { cell ->
                cell.trim().isNotEmpty() && cell.trim().all { it == '-' || it == ':' }
            }

    private fun tableCells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map(String::trim)

    private fun renderInline(text: String): String {
        val result = StringBuilder()
        var offset = 0
        while (offset < text.length) {
            val labelStart = text.indexOf('[', offset)
            if (labelStart < 0) break
            val labelEnd = text.indexOf("](", labelStart + 1)
            if (labelEnd < 0) break
            val urlEnd = text.indexOf(')', labelEnd + 2)
            if (urlEnd < 0) break

            result.append(escape(text.substring(offset, labelStart)))
            val label = text.substring(labelStart + 1, labelEnd)
            val rawUrl = text.substring(labelEnd + 2, urlEnd)
            val url = when {
                rawUrl.startsWith("https://") || rawUrl.startsWith("http://") || rawUrl.startsWith("/") -> rawUrl
                rawUrl.matches(Regex("[A-Za-z0-9._-]+")) -> "/licenses/$rawUrl"
                else -> null
            }
            if (url == null) {
                result.append(escape(text.substring(labelStart, urlEnd + 1)))
            } else {
                result.append("<a href=\"").append(escapeAttribute(url)).append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                    .append(escape(label)).append("</a>")
            }
            offset = urlEnd + 1
        }
        result.append(escape(text.substring(offset)))
        return result.toString()
    }

    private fun escapeCode(text: String): String =
        buildString(text.length) {
            text.forEach { char ->
                append(
                    when (char) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        '\'' -> "&#39;"
                        else -> char
                    },
                )
            }
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
