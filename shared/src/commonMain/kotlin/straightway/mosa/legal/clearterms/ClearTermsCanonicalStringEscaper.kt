package straightway.mosa.legal.clearterms

class ClearTermsCanonicalStringEscaper {
    fun render(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '"' -> {
                        append("\\\"")
                    }

                    '\\' -> {
                        append("\\\\")
                    }

                    '\b' -> {
                        append("\\b")
                    }

                    '\u000c' -> {
                        append("\\f")
                    }

                    '\n' -> {
                        append("\\n")
                    }

                    '\r' -> {
                        append("\\r")
                    }

                    '\t' -> {
                        append("\\t")
                    }

                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
}
