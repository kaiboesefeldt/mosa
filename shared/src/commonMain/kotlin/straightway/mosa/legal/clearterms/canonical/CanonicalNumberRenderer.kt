package straightway.mosa.legal.clearterms.canonical

import straightway.mosa.legal.clearterms.validation.ValidationException

class CanonicalNumberRenderer {
    fun render(value: Double): String {
        if (!value.isFinite()) {
            throw ValidationException("Invalid number value")
        }
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) {
            asLong.toString()
        } else {
            value.toString()
        }
    }
}
