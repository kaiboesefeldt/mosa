package straightway.mosa.legal.clearterms.parse

import straightway.mosa.legal.clearterms.ClearTermsValidationException

class ClearTermsJsonParser {
    fun parse(json: String): ClearTermsJsonNode.ObjectNode {
        val reader = ClearTermsJsonValueReader(json)
        val value = reader.parseValue()
        reader.skipWhitespace()
        if (reader.hasRemaining()) {
            throw ClearTermsValidationException("Trailing content after JSON value")
        }
        return value as? ClearTermsJsonNode.ObjectNode
            ?: throw ClearTermsValidationException("Root JSON value must be an object")
    }
}
