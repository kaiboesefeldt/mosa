package straightway.mosa.content

import kotlinx.coroutines.flow.toList
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.time.Instant

internal class ContentCsvExportService(
    private val vectorStore: VectorStore,
) {
    suspend fun exportCsvForUser(userGID: String): String {
        val lines =
            vectorStore
                .dumpAll()
                .toList()
                .asSequence()
                .filter { it.fields.requireContentMetadata(it.id, ContentMetadataField.AuthorGid) == userGID }
                .map { it.toCsvExportEntry() }
                .sortedWith(compareBy<CsvExportEntry> { it.storedAtEpoch }.thenBy { it.id })
                .map { it.toCsvRow() }
                .toList()
        return buildString {
            appendLine(CSV_HEADER)
            lines.forEach { appendLine(it) }
        }.trimEnd()
    }

    private fun VectorStore.PostDump.toCsvExportEntry(): CsvExportEntry {
        val storedAtEpoch = fields.requireContentMetadata(id, ContentMetadataField.ReceivedAt).toLong()
        return CsvExportEntry(
            id = id,
            type = if (fields[ROOT_ID_KEY] == null) "post" else "reply",
            rootId = fields[ROOT_ID_KEY].orEmpty(),
            storedAtEpoch = storedAtEpoch,
            storedAtIso8601 = Instant.ofEpochSecond(storedAtEpoch).toString(),
            authorGid = fields.requireContentMetadata(id, ContentMetadataField.AuthorGid),
            text = text,
        )
    }

    private data class CsvExportEntry(
        val id: String,
        val type: String,
        val rootId: String,
        val storedAtEpoch: Long,
        val storedAtIso8601: String,
        val authorGid: String,
        val text: String,
    ) {
        fun toCsvRow(): String =
            listOf(
                csvField(id),
                csvField(type),
                csvField(rootId),
                csvField(storedAtEpoch.toString()),
                csvField(storedAtIso8601),
                csvField(authorGid),
                csvField(text, alwaysQuote = true),
            ).joinToString(",")
    }

    private companion object {
        private const val CSV_HEADER = "id,type,root_id,stored_at_epoch,stored_at_iso8601,author_gid,text"
    }
}

private fun csvField(
    value: String,
    alwaysQuote: Boolean = false,
): String {
    val escaped = value.replace("\"", "\"\"")
    return if (alwaysQuote || escaped != value || value.contains(',') || value.contains('\n') || value.contains('\r')) {
        "\"$escaped\""
    } else {
        escaped
    }
}
