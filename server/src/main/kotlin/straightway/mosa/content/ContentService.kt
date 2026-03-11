package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.flow.toList
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime

class ContentService(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val hasherFactory: () -> MessageDigest,
    private val maxFetchItems: Int,
    private val replyIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun publish(
        userGID: String,
        request: PublishRequest,
        receiveTimestamp: Long,
    ): String =
        writeContent(userGID, request, emptyMap(), receiveTimestamp) {
            request.text.hashId()
        }

    class RootNotFoundException : BadRequestException("Root not found")

    class MissingRootIdInReplyException : BadRequestException("Replies must target root posts")

    class ContentNotFoundException : RuntimeException("post not found")

    class ContentForbiddenException : RuntimeException("not allowed to delete this post")

    suspend fun reply(
        userGID: String,
        request: ReplyRequest,
        receiveTimestamp: Long,
    ): String {
        validateLength(request.content.text)
        val root =
            vectorStore
                .query(VectorStore.Query(id = request.rootId))
                .resultsPage
                .singleOrNull() ?: throw RootNotFoundException()
        if (root.fields[ROOT_ID_KEY] != null) throw MissingRootIdInReplyException()
        return writeContent(
            userGID,
            request.content,
            mapOf(
                ROOT_ID_KEY to request.rootId,
            ),
            receiveTimestamp,
        ) {
            replyIdFactory()
        }
    }

    suspend fun delete(
        userGID: String,
        id: String,
    ): List<String> {
        if (id.isBlank()) throw BadRequestException("id must not be blank")
        val post = vectorStore.fetch(listOf(id)).singleOrNull() ?: throw ContentNotFoundException()
        val author = post.fields.requireContentMetadata(post.id, ContentMetadataField.AuthorGid)
        if (author != userGID) throw ContentForbiddenException()
        val deletedIds = linkedSetOf<String>()
        vectorStore.writeAccess {
            remove(post.id)
        }
        deletedIds.add(post.id)
        if (post.fields[ROOT_ID_KEY] == null) {
            deleteRepliesForRoot(post.id, deletedIds)
        }
        return deletedIds.toList()
    }

    suspend fun deleteAllForUser(userGID: String): List<String> {
        val deletedIds = linkedSetOf<String>()
        while (true) {
            val queryResult =
                vectorStore.query(
                    VectorStore.Query(fields = mapOf(AUTHOR_GID_KEY to userGID)),
                )
            if (queryResult.resultsPage.isEmpty()) return deletedIds.toList()
            val rootIds = queryResult.resultsPage.filter { it.fields[ROOT_ID_KEY] == null }.map { it.id }
            removeQueryResults(queryResult, deletedIds)
            rootIds.forEach { deleteRepliesForRoot(it, deletedIds) }
        }
    }

    suspend fun query(request: QueryRequest): QueryResult {
        if (request == nilQueryRequest) throw BadRequestException("no search criteria")
        val topicEmbedding = request.topic?.let { embeddingService.embed(it) }
        val queryResults =
            vectorStore.query(
                VectorStore.Query(
                    vector = topicEmbedding,
                    fromTime = request.fromTime?.let { Instant.ofEpochSecond(it) },
                    toTime = request.toTime?.let { Instant.ofEpochSecond(it) },
                    scoreMustBeAbove = request.scoreMustBeAbove,
                    scoreMustBeBelow = request.scoreMustBeBelow,
                    fields = mapOf(ROOT_ID_KEY to request.threadRootId),
                ),
            )

        return queryResults.toQueryResult()
    }

    suspend fun queryOn(request: QueryOnRequest): QueryResult {
        val queryOnId =
            runCatching {
                if (request.queryOnId.isEmpty()) null else UUID.fromString(request.queryOnId)
            }.getOrNull()
        val queryResults = vectorStore.queryOn(queryOnId)

        return queryResults.toQueryResult()
    }

    suspend fun search(request: QueryRequest): SearchResult = query(request).toSearchResult()

    suspend fun searchOn(request: QueryOnRequest): SearchResult = queryOn(request).toSearchResult()

    suspend fun fetch(request: FetchRequest): FetchResult {
        if (maxFetchItems < request.itemIds.size) {
            throw BadRequestException("too many itemIds")
        }
        val entries = vectorStore.fetch(request.itemIds).map { it.toFetchEntry() }
        return FetchResult(entries)
    }

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

    private fun QueryResult.toSearchResult(): SearchResult =
        SearchResult(
            queryOnId = queryOnId,
            entries = entries.map { SearchResult.Entry(it.id, it.similarity) },
        )

    @OptIn(ExperimentalTime::class)
    private suspend fun writeContent(
        userGID: String,
        request: PublishRequest,
        additionalFields: Map<String, String>,
        receiveTimestamp: Long,
        idProvider: () -> String,
    ): String {
        validateLength(request.text)
        val embeddingVector = embeddingService.embed(request.text)
        val id = idProvider()
        vectorStore.writeAccess {
            add(
                id = id,
                text = request.text,
                fields =
                    mapOf(
                        AUTHOR_GID_KEY to userGID,
                        RECEIVED_AT_KEY to receiveTimestamp.toString(),
                    ) + additionalFields,
                vector = embeddingVector,
            )
        }
        return id
    }

    private fun VectorStore.QueryResult.toQueryResultEntry(): QueryResult.Entry =
        QueryResult.Entry(
            id = id,
            content = text,
            similarity = similarity,
            rootId = fields[ROOT_ID_KEY],
            authorGID = fields.requireContentMetadata(id, ContentMetadataField.AuthorGid),
            storedAt =
                fields.requireContentMetadata(id, ContentMetadataField.ReceivedAt).toLong(),
        )

    private fun VectorStore.PostDump.toFetchEntry(): FetchResult.Entry =
        FetchResult.Entry(
            id,
            text,
            authorGID = fields.requireContentMetadata(id, ContentMetadataField.AuthorGid),
            storedAt = fields.requireContentMetadata(id, ContentMetadataField.ReceivedAt).toLong(),
            rootId = fields[ROOT_ID_KEY],
        )

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

    private fun VectorStore.QueryResults.toQueryResult(): QueryResult =
        QueryResult(
            queryOnId =
                if (queryOnId == null) {
                    ""
                } else {
                    queryOnId.toString()
                },
            entries = resultsPage.map { it.toQueryResultEntry() },
        )

    private fun String.hashId(): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            hasherFactory().digest(toByteArray(Charsets.UTF_8)),
        )

    private companion object {
        private const val CSV_HEADER = "id,type,root_id,stored_at_epoch,stored_at_iso8601,author_gid,text"
        private val nilQueryRequest = QueryRequest()

        private fun validateLength(text: String) {
            if (text.length > MAX_POST_LENGTH) {
                throw BadRequestException("text must be at most $MAX_POST_LENGTH characters")
            }
        }

        private fun String.toCsvField(alwaysQuote: Boolean = false): String {
            val escaped = replace("\"", "\"\"")
            return if (alwaysQuote || escaped != this || contains(',') || contains('\n') || contains('\r')) {
                "\"$escaped\""
            } else {
                escaped
            }
        }
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
                id.toCsvField(),
                type.toCsvField(),
                rootId.toCsvField(),
                storedAtEpoch.toString().toCsvField(),
                storedAtIso8601.toCsvField(),
                authorGid.toCsvField(),
                text.toCsvField(alwaysQuote = true),
            ).joinToString(",")
    }

    private suspend fun deleteRepliesForRoot(
        rootId: String,
        deletedIds: MutableSet<String>,
    ) {
        while (true) {
            val replies =
                vectorStore.query(
                    VectorStore.Query(fields = mapOf(ROOT_ID_KEY to rootId)),
                )
            if (replies.resultsPage.isEmpty()) return
            removeQueryResults(replies, deletedIds)
        }
    }

    private suspend fun removeQueryResults(
        results: VectorStore.QueryResults,
        deletedIds: MutableSet<String>,
    ) {
        vectorStore.writeAccess {
            results.resultsPage.forEach {
                remove(it.id)
                deletedIds.add(it.id)
            }
        }
    }
}
