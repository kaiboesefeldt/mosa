package straightway.mosa.legal.clearterms

fun interface ClearTermsResourceReader {
    fun readText(path: String): String
}
