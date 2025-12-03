package straightway.mosa.content

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.BadRequestException
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
) {
    suspend fun publish(
        userGID: String,
        request: PublishRequest,
        receiveTimestamp: Long,
    ): String =
        trace(userGID, request) {
            writeContent(userGID, request, emptyMap(), receiveTimestamp)
        }

    class RootNotFoundException : BadRequestException("Root not found")

    class MissingRootIdInReplyException : BadRequestException("Replies must target root posts")

    suspend fun reply(
        userGID: String,
        request: ReplyRequest,
        receiveTimestamp: Long,
    ): String =
        trace(userGID, request) {
            val root =
                vectorStore
                    .query(VectorStore.Query(id = request.rootId))
                    .resultsPage
                    .singleOrNull() ?: throw RootNotFoundException()
            if (root.fields[ROOT_ID_KEY] != null) throw MissingRootIdInReplyException()
            writeContent(
                userGID,
                request.content,
                mapOf(
                    ROOT_ID_KEY to request.rootId,
                ),
                receiveTimestamp,
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
                        fromTime = request.minTime?.let { Instant.ofEpochSecond(it) },
                        fields = request.threadRootId?.let { mapOf(ROOT_ID_KEY to it) } ?: emptyMap(),
                    ),
                )
            val filteredEntries =
                queryResults.resultsPage.filter { entry ->
                    request.scoreMustBeAbove?.let { entry.similarity > it } ?: true
                }

            return QueryResult(
                queryOnId = queryResults.queryOnId?.toString() ?: "",
                entries = filteredEntries.map { it.toQueryResultEntry() },
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
        additionalFields: Map<String, String>,
        receiveTimestamp: Long,
    ): String {
        val embeddingVector = embeddingService.embed(request.text)
        val id = request.text.hashId()
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
            authorGID = fields.requireMetadata(id, AUTHOR_GID_KEY),
            storedAt =
                fields.requireMetadata(id, RECEIVED_AT_KEY).toLong(),
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
        private val nilQueryRequest = QueryRequest()

        private fun Map<String, String>.requireMetadata(
            entryId: String,
            fieldName: String,
        ): String = get(fieldName) ?: missingMetadata(entryId, fieldName)

        private fun missingMetadata(
            entryId: String,
            fieldName: String,
        ): Nothing {
            log.error { "Vector store entry $entryId is missing metadata field $fieldName" }
            throw MissingVectorMetadataException(entryId, fieldName)
        }

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
