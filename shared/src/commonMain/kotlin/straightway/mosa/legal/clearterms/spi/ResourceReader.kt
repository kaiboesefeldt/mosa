package straightway.mosa.legal.clearterms.spi

fun interface ResourceReader {
    fun readText(path: String): String
}
