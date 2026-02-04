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
    private val maxFetchItems: Int,
) {
    suspend fun publish(
        userGID: String,
        request: PublishRequest,
        receiveTimestamp: Long,
    ): String = writeContent(userGID, request, emptyMap(), receiveTimestamp)

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
        )
    }

    suspend fun delete(
        userGID: String,
        id: String,
    ) {
        if (id.isBlank()) throw BadRequestException("id must not be blank")
        val post = vectorStore.fetch(listOf(id)).singleOrNull() ?: throw ContentNotFoundException()
        val author = post.fields.requireMetadata(post.id, AUTHOR_GID_KEY)
        if (author != userGID) throw ContentForbiddenException()
        vectorStore.writeAccess {
            remove(post.id)
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

        return QueryResult(
            queryOnId = queryResults.queryOnId?.toString() ?: "",
            entries = queryResults.resultsPage.map { it.toQueryResultEntry() },
        )
    }

    suspend fun queryOn(request: QueryOnRequest): QueryResult {
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

    suspend fun search(request: QueryRequest): SearchResult = query(request).toSearchResult()

    suspend fun searchOn(request: QueryOnRequest): SearchResult = queryOn(request).toSearchResult()

    suspend fun fetch(request: FetchRequest): FetchResult =
        if (maxFetchItems < request.itemIds.size) {
            throw BadRequestException("too many itemIds")
        } else {
            FetchResult(
                vectorStore.fetch(request.itemIds).map {
                    FetchResult.Entry(
                        it.id,
                        it.text,
                        authorGID = it.fields.requireMetadata(it.id, AUTHOR_GID_KEY),
                        storedAt = it.fields.requireMetadata(it.id, RECEIVED_AT_KEY).toLong(),
                        rootId = it.fields[ROOT_ID_KEY],
                    )
                },
            )
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
    ): String {
        validateLength(request.text)
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

    private companion object {
        private val log = KotlinLogging.logger { }
        private const val MAX_POST_LENGTH = 1800
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

        private fun validateLength(text: String) {
            if (text.length > MAX_POST_LENGTH) {
                throw BadRequestException("text must be at most $MAX_POST_LENGTH characters")
            }
        }
    }
}
