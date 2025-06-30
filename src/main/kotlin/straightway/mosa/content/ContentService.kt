package straightway.mosa.content

import io.github.oshai.kotlinlogging.KotlinLogging
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import java.security.MessageDigest
import java.util.UUID

class ContentService(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val hasherFactory: () -> MessageDigest,
) {
    suspend fun publish(request: PublishRequest) =
        trace(request) {
            writeContent(request)
        }

    suspend fun reply(request: ReplyRequest) =
        trace(request) {
            writeContent(request.content, mapOf("parentId" to request.parentId))
        }

    suspend fun query(request: QueryRequest): QueryResult =
        trace(request) {
            val searchResults =
                vectorStore.search(
                    request.embeddingVector.toFloatArray(),
                    request.minSimilarity,
                )

            return QueryResult(
                queryOnId = searchResults.searchOnId?.toString() ?: "",
                entries = searchResults.resultsPage.map { it.toQueryResultEntry() },
            )
        }

    suspend fun queryOn(request: QueryOnRequest): QueryResult =
        trace(request) {
            val searchOnId =
                runCatching {
                    if (request.queryOnId.isEmpty()) null else UUID.fromString(request.queryOnId)
                }.getOrNull()
            val searchResults = vectorStore.searchOn(searchOnId)

            return QueryResult(
                queryOnId = searchResults.searchOnId?.toString() ?: "",
                entries = searchResults.resultsPage.map { it.toQueryResultEntry() },
            )
        }

    private suspend fun writeContent(
        request: PublishRequest,
        additionalFields: Map<String, String> = emptyMap(),
    ) {
        val embeddingVector = embeddingService.embed(request.text)
        val id = request.text.hashId()
        vectorStore.writeAccess {
            add(
                id = id,
                text = request.text,
                fields =
                    mapOf(
                        "authorGID" to request.authorGID,
                        "receivedAt" to request.receivedAt.toString(),
                    ) + additionalFields,
                vector = embeddingVector,
            )
        }
    }

    private fun VectorStore.SearchResult.toQueryResultEntry(): QueryResult.Entry =
        QueryResult.Entry(
            id = id,
            content = text,
            similarity = similarity,
            parent = fields["parentId"],
            authorGID = fields["authorGID"] ?: "",
            storedAt = fields["receivedAt"]?.toLongOrNull() ?: 0L,
        )

    private fun String.hashId(): String =
        hasherFactory()
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private suspend inline fun <T> trace(
        request: Any,
        block: suspend () -> T,
    ): T {
        val requestId = UUID.randomUUID()
        log.debug { "START $requestId handling $request" }
        return try {
            block()
        } finally {
            log.debug { "FINISH $requestId handling $request" }
        }
    }

    private companion object {
        private val log = KotlinLogging.logger { }
    }
}
