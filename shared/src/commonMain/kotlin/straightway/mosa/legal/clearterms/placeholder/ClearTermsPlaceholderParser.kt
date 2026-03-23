package straightway.mosa.legal.clearterms.placeholder

import straightway.mosa.legal.clearterms.ClearTermsValidationException

class ClearTermsPlaceholderParser(
    private val contentParser: ClearTermsPlaceholderContentParser = ClearTermsPlaceholderContentParser(),
) {
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
                    flushLiteral(buffer, segments)
                    val endIndex = findPlaceholderEnd(text, index + 2)
                    if (endIndex < 0) {
                        throw ClearTermsValidationException("Unterminated placeholder")
                    }
                    val placeholderText = text.substring(index + 2, endIndex)
                    segments += Segment.Placeholder(contentParser.parse(placeholderText))
                    index = endIndex + 2
                }

                else -> {
                    buffer.append(text[index])
                    index += 1
                }
            }
        }
        flushLiteral(buffer, segments)
        return segments
    }

    private fun flushLiteral(
        buffer: StringBuilder,
        segments: MutableList<Segment>,
    ) {
        if (buffer.isNotEmpty()) {
            segments += Segment.Literal(buffer.toString())
            buffer.clear()
        }
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

    sealed class Segment {
        data class Literal(
            val text: String,
        ) : Segment()

        data class Placeholder(
            val placeholder: ClearTermsPlaceholder,
        ) : Segment()
    }
}
