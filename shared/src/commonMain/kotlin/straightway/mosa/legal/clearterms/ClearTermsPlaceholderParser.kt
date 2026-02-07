package straightway.mosa.legal.clearterms

class ClearTermsPlaceholderParser {
    fun parse(text: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        val buffer = StringBuilder()
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("\\{{", index) -> {
                    buffer.append("{{")
                    index += 3
                }
                text.startsWith("\\}}", index) -> {
                    buffer.append("}}")
                    index += 3
                }
                text.startsWith("{{", index) -> {
                    if (buffer.isNotEmpty()) {
                        segments += Segment.Literal(buffer.toString())
                        buffer.clear()
                    }
                    val endIndex = findPlaceholderEnd(text, index + 2)
                    if (endIndex < 0) {
                        throw ClearTermsValidationException("Unterminated placeholder")
                    }
                    val placeholderText = text.substring(index + 2, endIndex)
                    segments += Segment.Placeholder(parsePlaceholder(placeholderText))
                    index = endIndex + 2
                }
                else -> {
                    buffer.append(text[index])
                    index += 1
                }
            }
        }
        if (buffer.isNotEmpty()) {
            segments += Segment.Literal(buffer.toString())
        }
        return segments
    }

    private fun findPlaceholderEnd(
        text: String,
        startIndex: Int,
    ): Int {
        var index = startIndex
        while (index < text.length - 1) {
            if (text.startsWith("\\}}", index)) {
                index += 3
                continue
            }
            if (text.startsWith("}}", index)) {
                return index
            }
            index += 1
        }
        return -1
    }

    private fun parsePlaceholder(content: String): ClearTermsPlaceholder {
        val (main, fallback) = splitFallback(content)
        val parts = main.split(":", limit = 2)
        if (parts.size != 2) {
            throw ClearTermsValidationException("Invalid placeholder syntax")
        }
        val type = parseType(parts[0])
        val key = parts[1]
        if (key.isBlank() || key.any { it.isWhitespace() }) {
            throw ClearTermsValidationException("Invalid placeholder key")
        }
        if (type == ClearTermsPlaceholderType.STRING && fallback != null) {
            throw ClearTermsValidationException("String placeholders must not define fallbacks")
        }
        return ClearTermsPlaceholder(type, key, fallback)
    }

    private fun splitFallback(content: String): Pair<String, String?> {
        val markerIndex = content.indexOf("??")
        if (markerIndex < 0) {
            return content to null
        }
        val main = content.substring(0, markerIndex)
        val fallback = content.substring(markerIndex + 2)
        return main to fallback
    }

    private fun parseType(value: String): ClearTermsPlaceholderType =
        when (value) {
            "string" -> ClearTermsPlaceholderType.STRING
            "number" -> ClearTermsPlaceholderType.NUMBER
            "duration" -> ClearTermsPlaceholderType.DURATION
            "time" -> ClearTermsPlaceholderType.TIME
            "weekday" -> ClearTermsPlaceholderType.WEEKDAY
            else -> throw ClearTermsValidationException("Unsupported placeholder type")
        }

    sealed class Segment {
        data class Literal(
            val text: String,
        ) : Segment()

        data class Placeholder(
            val placeholder: ClearTermsPlaceholder,
        ) : Segment()
    }
}
