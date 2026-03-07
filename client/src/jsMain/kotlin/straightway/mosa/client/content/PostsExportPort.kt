package straightway.mosa.client.content

fun interface PostsExportPort {
    suspend fun export(fileBaseName: String)
}
