package straightway.mosa.legal.clearterms

interface ClearTermsResourceReader {
    fun readText(path: String): String
}
