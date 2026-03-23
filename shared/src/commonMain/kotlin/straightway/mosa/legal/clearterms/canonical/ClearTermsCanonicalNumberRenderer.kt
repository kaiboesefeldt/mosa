package straightway.mosa.legal.clearterms.canonical

import straightway.mosa.legal.clearterms.ClearTermsValidationException

class ClearTermsCanonicalNumberRenderer {
    fun render(value: Double): String {
        if (!value.isFinite()) {
            throw ClearTermsValidationException("Invalid number value")
        }
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) {
            asLong.toString()
        } else {
            value.toString()
        }
    }
}
