package straightway.mosa.content

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.BadRequestException
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime

class ContentService(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val hasherFactory: () -> MessageDigest,
    private val clock: Clock,
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
        val parent =
            vectorStore
                .query(VectorStore.Query(id = request.parentId))
                .resultsPage
                .singleOrNull() ?: throw BadRequestException("Parent not found")
        val rootId = parent.fields[ROOT_ID_KEY] ?: request.parentId
        writeContent(
            userGID,
            request.content,
            mapOf(
                PARENT_ID_KEY to request.parentId,
                ROOT_ID_KEY to rootId,
            ),
        )
    }

    suspend fun query(
        userGID: String,
        request: QueryRequest,
    ): QueryResult =
        trace(userGID, request) {
            if (request == nilQueryRequest) throw BadRequestException("no search criteria")
            val topicEmbedding = request.topic?.let { embeddingService.embed(it) }
            val queryResults =
                vectorStore.query(
                    VectorStore.Query(
                        vector = topicEmbedding,
                        fromTime = request.minTime,
                        fields = request.threadRootId?.let { mapOf(ROOT_ID_KEY to it) } ?: emptyMap(),
                    ),
                )

            return QueryResult(
                queryOnId = queryResults.queryOnId?.toString() ?: "",
                entries = queryResults.resultsPage.map { it.toQueryResultEntry() },
            )
        }

    suspend fun queryOn(
        userGID: String,
        request: QueryOnRequest,
    ): QueryResult =
        trace(userGID, request) {
            val queryOnId =
                runCatching {
                    if (request.queryOnId.isEmpty()) null else UUID.fromString(request.queryOnId)
                }.getOrNull()
            val queryResults = vectorStore.queryOn(queryOnId)

            return QueryResult(
                queryOnId = queryResults.queryOnId?.toString() ?: "",
                entries = queryResults.resultsPage.map { it.toQueryResultEntry() },
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
                        "receivedAt" to clock.instant().epochSecond.toString(),
                    ) + additionalFields,
                vector = embeddingVector,
            )
        }
    }

    private fun VectorStore.QueryResult.toQueryResultEntry(): QueryResult.Entry =
        QueryResult.Entry(
            id = id,
            content = text,
            similarity = similarity,
            parent = fields[PARENT_ID_KEY],
            authorGID = fields["authorGID"] ?: throw RuntimeException("No authorGID field set for vector store entry $id"),
            storedAt =
                Instant.ofEpochSecond(
                    (
                        fields["receivedAt"]
                            ?: throw RuntimeException("No receivedAt field set for vector store entry $id")
                    ).toLong(),
                ),
        )

    private fun String.hashId(): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            hasherFactory().digest(toByteArray(Charsets.UTF_8)),
        )

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
        private const val PARENT_ID_KEY = "parentId"
        private const val ROOT_ID_KEY = "rootId"
        private val nilQueryRequest = QueryRequest()

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
