package straightway.mosa.legal.clearterms

class ClearTermsJsonValueReader(
    private val text: String,
) {
    private var index: Int = 0

    fun parseValue(): ClearTermsJsonNode {
        skipWhitespace()
        ensureInputAvailable()
        return when (val ch = peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> ClearTermsJsonNode.ScalarNode.StringNode(parseString())
            'n' -> parseNull()
            't', 'f' -> parseBoolean()
            else -> parseNumericValue(ch)
        }
    }

    fun skipWhitespace() {
        while (hasRemaining() && peek().isWhitespace()) {
            index += 1
        }
    }

    fun hasRemaining(): Boolean = index < text.length

    private fun parseObject(): ClearTermsJsonNode.ObjectNode {
        expect('{')
        skipWhitespace()
        val entries = linkedMapOf<String, ClearTermsJsonNode>()
        if (consumeIf('}')) {
            return ClearTermsJsonNode.ObjectNode(entries)
        }
        while (true) {
            val key = parseObjectKey()
            if (entries.containsKey(key)) {
                throw ClearTermsValidationException("Duplicate key '$key'")
            }
            skipWhitespace()
            expect(':')
            entries[key] = parseValue()
            skipWhitespace()
            if (!consumeListDelimiterUntil('}', "Unterminated object", "Expected ',' or '}'")) {
                break
            }
        }
        return ClearTermsJsonNode.ObjectNode(entries)
    }

    private fun parseArray(): ClearTermsJsonNode.ArrayNode {
        expect('[')
        skipWhitespace()
        val items = mutableListOf<ClearTermsJsonNode>()
        if (consumeIf(']')) {
            return ClearTermsJsonNode.ArrayNode(items)
        }
        while (true) {
            items += parseValue()
            skipWhitespace()
            if (!consumeListDelimiterUntil(']', "Unterminated array", "Expected ',' or ']' ")) {
                break
            }
        }
        return ClearTermsJsonNode.ArrayNode(items)
    }

    private fun parseNull(): ClearTermsJsonNode.ScalarNode.NullNode {
        expectSequence("null")
        return ClearTermsJsonNode.ScalarNode.NullNode
    }

    private fun parseBoolean(): ClearTermsJsonNode.ScalarNode.BooleanNode =
        when {
            text.startsWith("true", index) -> {
                index += 4
                ClearTermsJsonNode.ScalarNode.BooleanNode(true)
            }

            text.startsWith("false", index) -> {
                index += 5
                ClearTermsJsonNode.ScalarNode.BooleanNode(false)
            }

            else -> {
                throw ClearTermsValidationException("Expected boolean")
            }
        }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (hasRemaining()) {
            when (val ch = next()) {
                '"' -> {
                    return result.toString()
                }

                '\\' -> {
                    result.append(parseEscape())
                }

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
        if (!hasRemaining()) {
            throw ClearTermsValidationException("Unterminated escape")
        }
        return when (next()) {
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
        consumeOptionalSign()
        if (hasRemaining() && peek() == '0') {
            index += 1
        } else {
            readDigits()
        }
        parseFractionPart()
        parseExponentPart()
        val numberText = text.substring(start, index)
        return numberText.toDoubleOrNull()
            ?: throw ClearTermsValidationException("Invalid number format")
    }

    private fun readDigits() {
        val start = index
        while (hasRemaining() && peek().isDigit()) {
            index += 1
        }
        if (start == index) {
            throw ClearTermsValidationException("Expected digit")
        }
    }

    private fun expect(expected: Char) {
        if (!hasRemaining() || peek() != expected) {
            throw ClearTermsValidationException("Expected '$expected'")
        }
        index += 1
    }

    @Suppress("SameParameterValue")
    private fun expectSequence(sequence: String) {
        if (!text.startsWith(sequence, index)) {
            throw ClearTermsValidationException("Expected '$sequence'")
        }
        index += sequence.length
    }

    private fun ensureInputAvailable() {
        if (!hasRemaining()) {
            throw ClearTermsValidationException("Unexpected end of JSON input")
        }
    }

    private fun parseNumericValue(ch: Char): ClearTermsJsonNode.ScalarNode.NumberNode {
        if (ch != '-' && !ch.isDigit()) {
            throw ClearTermsValidationException("Unexpected character '$ch'")
        }
        return ClearTermsJsonNode.ScalarNode.NumberNode(parseNumber())
    }

    private fun parseObjectKey(): String {
        skipWhitespace()
        return parseString()
    }

    private fun consumeListDelimiterUntil(
        endToken: Char,
        unterminatedMessage: String,
        invalidDelimiterMessage: String,
    ): Boolean {
        val continueToken = ','
        if (!hasRemaining()) {
            throw ClearTermsValidationException(unterminatedMessage)
        }
        return when (peek()) {
            continueToken -> {
                index += 1
                true
            }

            endToken -> {
                index += 1
                false
            }

            else -> {
                throw ClearTermsValidationException(invalidDelimiterMessage)
            }
        }
    }

    private fun consumeIf(token: Char): Boolean {
        if (hasRemaining() && peek() == token) {
            index += 1
            return true
        }
        return false
    }

    private fun consumeOptionalSign() {
        if (peek() == '-') {
            index += 1
        }
    }

    private fun parseFractionPart() {
        if (hasRemaining() && peek() == '.') {
            index += 1
            readDigits()
        }
    }

    private fun parseExponentPart() {
        if (hasRemaining() && peek().lowercaseChar() == 'e') {
            index += 1
            if (hasRemaining() && (peek() == '+' || peek() == '-')) {
                index += 1
            }
            readDigits()
        }
    }

    private fun peek(): Char = text[index]

    private fun next(): Char = text[index++]
}
