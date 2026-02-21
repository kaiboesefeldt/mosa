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
            text.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(char)
                }
            }
        }
}
