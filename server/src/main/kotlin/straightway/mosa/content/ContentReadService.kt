package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.time.Instant
import java.util.UUID

internal class ContentReadService(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val maxFetchItems: Int,
) {
    suspend fun query(request: QueryRequest): QueryResult {
        if (request == QueryRequest()) throw BadRequestException("no search criteria")
        val topicEmbedding = request.topic?.let { embeddingService.embed("query: $it") }
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

    suspend fun fetch(request: FetchRequest): FetchResult {
        if (maxFetchItems < request.itemIds.size) {
            throw BadRequestException("too many itemIds")
        }
        val entries = vectorStore.fetch(request.itemIds).map { it.toFetchEntry() }
        return FetchResult(entries)
    }

    private fun VectorStore.QueryResult.toQueryResultEntry(): QueryResult.Entry =
        QueryResult.Entry(
            id = id,
            content = text,
            similarity = similarity,
            rootId = fields[ROOT_ID_KEY],
            authorGID = fields.requireContentMetadata(id, ContentMetadataField.AuthorGid),
            authorPublicKey = fields.requireContentMetadata(id, ContentMetadataField.AuthorPublicKey),
            authorSignature = fields.requireContentMetadata(id, ContentMetadataField.AuthorSignature),
            signatureVersion = fields.requireContentMetadata(id, ContentMetadataField.SignatureVersion).toInt(),
            storedAt = fields.requireContentMetadata(id, ContentMetadataField.ReceivedAt).toLong(),
        )

    private fun VectorStore.PostDump.toFetchEntry(): FetchResult.Entry =
        FetchResult.Entry(
            id,
            text,
            authorGID = fields.requireContentMetadata(id, ContentMetadataField.AuthorGid),
            storedAt = fields.requireContentMetadata(id, ContentMetadataField.ReceivedAt).toLong(),
            rootId = fields[ROOT_ID_KEY],
            authorPublicKey = fields.requireContentMetadata(id, ContentMetadataField.AuthorPublicKey),
            authorSignature = fields.requireContentMetadata(id, ContentMetadataField.AuthorSignature),
            signatureVersion = fields.requireContentMetadata(id, ContentMetadataField.SignatureVersion).toInt(),
        )

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
}
