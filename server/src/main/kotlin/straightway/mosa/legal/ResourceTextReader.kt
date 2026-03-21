package straightway.mosa.legal

fun interface ResourceTextReader {
    fun read(path: String): String?
}
