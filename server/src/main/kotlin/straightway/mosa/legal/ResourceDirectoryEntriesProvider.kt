package straightway.mosa.legal

fun interface ResourceDirectoryEntriesProvider {
    fun list(directoryPath: String): List<String>?
}
