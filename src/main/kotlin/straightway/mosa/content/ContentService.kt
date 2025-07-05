package straightway.mosa.content

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.BadRequestException
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ContentService(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val hasherFactory: () -> MessageDigest,
) {
    suspend fun publish(
        userGID: String,
        request: PublishRequest,
    ) = trace(userGID, request) {
        writeContent(userGID, request)
    }

    suspend fun reply(
        userGID: String,
        request: ReplyRequest,
    ) = trace(userGID, request) {
        writeContent(userGID, request.content, mapOf("parentId" to request.parentId))
    }

    suspend fun query(
        userGID: String,
        request: SearchRequest,
    ): SearchResult =
        trace(userGID, request) {
            // TODO: Check null vector
            if (request.embeddingVector.size != embeddingService.dimension) {
                throw BadRequestException(
                    "Invalid embedding vector size (expected: ${embeddingService.dimension}, got: ${request.embeddingVector.size})",
                )
            }

            val searchResults =
                vectorStore.search(
                    request.embeddingVector.toFloatArray(),
                    request.minSimilarity,
                )

            return SearchResult(
                searchOnId = searchResults.searchOnId?.toString() ?: "",
                entries = searchResults.resultsPage.map { it.toQueryResultEntry() },
            )
        }

    suspend fun queryOn(
        userGID: String,
        request: SearchOnRequest,
    ): SearchResult =
        trace(userGID, request) {
            val searchOnId =
                runCatching {
                    if (request.searchOnId.isEmpty()) null else UUID.fromString(request.searchOnId)
                }.getOrNull()
            val searchResults = vectorStore.searchOn(searchOnId)

            return SearchResult(
                searchOnId = searchResults.searchOnId?.toString() ?: "",
                entries = searchResults.resultsPage.map { it.toQueryResultEntry() },
            )
        }

    @OptIn(ExperimentalTime::class)
    private suspend fun writeContent(
        userGID: String,
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
                        "authorGID" to userGID,
                        "receivedAt" to
                            Clock.System
                                .now()
                                .epochSeconds
                                .toString(),
                    ) + additionalFields,
                vector = embeddingVector,
            )
        }
    }

    private fun VectorStore.SearchResult.toQueryResultEntry(): SearchResult.Entry =
        SearchResult.Entry(
            id = id,
            content = text,
            similarity = similarity,
            parent = fields["parentId"],
            authorGID = fields["authorGID"] ?: throw RuntimeException("No authorGID field set for vector store entry $id"),
            storedAt = fields["receivedAt"]?.toLong() ?: throw RuntimeException("No receivedAt field set for vector store entry $id"),
        )

    private fun String.hashId(): String =
        hasherFactory()
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private suspend inline fun <T> trace(
        userGID: String,
        request: Any,
        block: suspend () -> T,
    ): T {
        val requestId = UUID.randomUUID()
        log.debug { "START $requestId: user: $userGID, request ${request.logString()}" }
        return try {
            block()
        } finally {
            log.debug { "FINISH $requestId: user: $userGID" }
        }
    }

    private companion object {
        private val log = KotlinLogging.logger { }
        private const val MAX_LOG_LEN_FOR_REQUESTS = 100

        private fun Any.logString(): String =
            toString().let {
                if (it.length <= MAX_LOG_LEN_FOR_REQUESTS) {
                    it
                } else {
                    it.substring(0..MAX_LOG_LEN_FOR_REQUESTS) + "..."
                }
            }
    }
}
