package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider

class PostsExportService(
    private val podSessionProvider: PodSessionProvider,
    private val downloader: PostsCsvDownloader,
) : PostsExportPort {
    override suspend fun export(fileBaseName: String) {
        val csvContent =
            podSessionProvider.execute("export posts csv") { session ->
                session.podAccess.exportPostsCsv(session.jwt)
            } ?: throw IllegalStateException("CSV export failed")
        downloader.download(csvContent, fileBaseName)
    }
}
