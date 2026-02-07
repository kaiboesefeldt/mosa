package straightway.mosa.legal.clearterms

class ClearTermsJsonParser {
    fun parse(json: String): ClearTermsJsonNode.ObjectNode {
        val input = JsonInput(json)
        val value = input.parseValue()
        input.skipWhitespace()
        if (!input.isAtEnd()) {
            throw ClearTermsValidationException("Trailing content after JSON value")
        }
        return value as? ClearTermsJsonNode.ObjectNode
            ?: throw ClearTermsValidationException("Root JSON value must be an object")
    }

    private class JsonInput(private val text: String) {
        private var index: Int = 0

        fun parseValue(): ClearTermsJsonNode {
            skipWhitespace()
            if (isAtEnd()) {
                throw ClearTermsValidationException("Unexpected end of JSON input")
            }
            return when (val ch = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> ClearTermsJsonNode.ScalarNode.StringNode(parseString())
                'n' -> parseNull()
                't', 'f' -> throw ClearTermsValidationException("Boolean values are not supported")
                else -> if (ch == '-' || ch.isDigit()) {
                    ClearTermsJsonNode.ScalarNode.NumberNode(parseNumber())
                } else {
                    throw ClearTermsValidationException("Unexpected character '$ch'")
                }
            }
        }

        fun skipWhitespace() {
            while (!isAtEnd() && peek().isWhitespace()) {
                index += 1
            }
        }

        fun isAtEnd(): Boolean = index >= text.length

        private fun parseObject(): ClearTermsJsonNode.ObjectNode {
            expect('{')
            skipWhitespace()
            val entries = linkedMapOf<String, ClearTermsJsonNode>()
            if (peekOrNull() == '}') {
                index += 1
                return ClearTermsJsonNode.ObjectNode(entries)
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                if (entries.containsKey(key)) {
                    throw ClearTermsValidationException("Duplicate key '$key'")
                }
                skipWhitespace()
                expect(':')
                val value = parseValue()
                entries[key] = value
                skipWhitespace()
                when (peekOrNull()) {
                    ',' -> {
                        index += 1
                        continue
                    }
                    '}' -> {
                        index += 1
                        break
                    }
                    null -> throw ClearTermsValidationException("Unterminated object")
                    else -> throw ClearTermsValidationException("Expected ',' or '}'")
                }
            }
            return ClearTermsJsonNode.ObjectNode(entries)
        }

        private fun parseArray(): ClearTermsJsonNode.ArrayNode {
            expect('[')
            skipWhitespace()
            val items = mutableListOf<ClearTermsJsonNode>()
            if (peekOrNull() == ']') {
                index += 1
                return ClearTermsJsonNode.ArrayNode(items)
            }
            while (true) {
                val value = parseValue()
                items += value
                skipWhitespace()
                when (peekOrNull()) {
                    ',' -> {
                        index += 1
                        continue
                    }
                    ']' -> {
                        index += 1
                        break
                    }
                    null -> throw ClearTermsValidationException("Unterminated array")
                    else -> throw ClearTermsValidationException("Expected ',' or ']' ")
                }
            }
            return ClearTermsJsonNode.ArrayNode(items)
        }

        private fun parseNull(): ClearTermsJsonNode.ScalarNode.NullNode {
            expectSequence("null")
            return ClearTermsJsonNode.ScalarNode.NullNode
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (!isAtEnd()) {
                val ch = next()
                when (ch) {
                    '"' -> return result.toString()
                    '\\' -> result.append(parseEscape())
                    else -> {
                        if (ch.code < 0x20) {
                            throw ClearTermsValidationException("Invalid control character in string")
                        }
                        result.append(ch)
                    }
                }
            }
            throw ClearTermsValidationException("Unterminated string")
        }

        private fun parseEscape(): Char {
            if (isAtEnd()) {
                throw ClearTermsValidationException("Unterminated escape")
            }
            return when (val ch = next()) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> parseUnicodeEscape()
                else -> throw ClearTermsValidationException("Invalid escape sequence")
            }
        }

        private fun parseUnicodeEscape(): Char {
            if (index + 4 > text.length) {
                throw ClearTermsValidationException("Invalid unicode escape")
            }
            val hex = text.substring(index, index + 4)
            if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                throw ClearTermsValidationException("Invalid unicode escape")
            }
            index += 4
            return hex.toInt(16).toChar()
        }

        private fun parseNumber(): Double {
            val start = index
            if (peekOrNull() == '-') {
                index += 1
            }
            if (peekOrNull() == '0') {
                index += 1
            } else {
                readDigits()
            }
            if (peekOrNull() == '.') {
                index += 1
                readDigits()
            }
            if (peekOrNull() == 'e' || peekOrNull() == 'E') {
                index += 1
                if (peekOrNull() == '+' || peekOrNull() == '-') {
                    index += 1
                }
                readDigits()
            }
            val numberText = text.substring(start, index)
            return numberText.toDoubleOrNull()
                ?: throw ClearTermsValidationException("Invalid number format")
        }

        private fun readDigits() {
            val start = index
            while (!isAtEnd() && peek().isDigit()) {
                index += 1
            }
            if (start == index) {
                throw ClearTermsValidationException("Expected digit")
            }
        }

        private fun expect(expected: Char) {
            if (isAtEnd() || peek() != expected) {
                throw ClearTermsValidationException("Expected '$expected'")
            }
            index += 1
        }

        private fun expectSequence(sequence: String) {
            if (!text.startsWith(sequence, index)) {
                throw ClearTermsValidationException("Expected '$sequence'")
            }
            index += sequence.length
        }

        private fun peek(): Char = text[index]

        private fun peekOrNull(): Char? = if (isAtEnd()) null else text[index]

        private fun next(): Char = text[index++]
    }
}
