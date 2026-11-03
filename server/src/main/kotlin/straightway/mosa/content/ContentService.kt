package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.content.complaint.PostComplaintStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime

class ContentService(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val hasherFactory: () -> MessageDigest,
    maxFetchItems: Int,
    private val replyIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val isAdmin: suspend (String) -> Boolean = { false },
    postComplaintStore: PostComplaintStore,
) {
    private val readService = ContentReadService(embeddingService, vectorStore, maxFetchItems)
    private val deleteService = ContentDeleteService(embeddingService, vectorStore, postComplaintStore, isAdmin)
    private val csvExportService = ContentCsvExportService(vectorStore)

    suspend fun publish(
        userGID: String,
        request: PublishRequest,
        receiveTimestamp: Long,
        authorPublicKey: String,
    ): String =
        writeContent(userGID, authorPublicKey, request, emptyMap(), receiveTimestamp) {
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
        authorPublicKey: String,
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
            authorPublicKey,
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
    ): List<String> = deleteService.delete(userGID, id)

    suspend fun deleteAllForUser(userGID: String): List<String> = deleteService.deleteAllForUser(userGID)

    suspend fun contentCapabilities(
        userGID: String,
        targetGID: String = userGID,
    ): ContentCapabilitiesResult = ContentCapabilitiesResult(isAdmin = isAdmin(targetGID))

    suspend fun query(request: QueryRequest): QueryResult = readService.query(request)

    suspend fun queryOn(request: QueryOnRequest): QueryResult = readService.queryOn(request)

    suspend fun search(request: QueryRequest): SearchResult = query(request).toSearchResult()

    suspend fun searchOn(request: QueryOnRequest): SearchResult = queryOn(request).toSearchResult()

    suspend fun fetch(request: FetchRequest): FetchResult = readService.fetch(request)

    suspend fun exportCsvForUser(userGID: String): String = csvExportService.exportCsvForUser(userGID)

    private fun QueryResult.toSearchResult(): SearchResult =
        SearchResult(
            queryOnId = queryOnId,
            entries = entries.map { SearchResult.Entry(it.id, it.similarity) },
        )

    @OptIn(ExperimentalTime::class)
    private suspend fun writeContent(
        userGID: String,
        authorPublicKey: String,
        request: PublishRequest,
        additionalFields: Map<String, String>,
        receiveTimestamp: Long,
        idProvider: () -> String,
    ): String {
        validateLength(request.text)
        val embeddingVector = embeddingService.embed("passage: ${request.text}")
        val id = idProvider()
        vectorStore.writeAccess {
            add(
                id = id,
                text = request.text,
                fields =
                    mapOf(
                        AUTHOR_GID_KEY to userGID,
                        AUTHOR_PUBLIC_KEY_KEY to authorPublicKey,
                        AUTHOR_SIGNATURE_KEY to request.signature,
                        SIGNATURE_VERSION_KEY to request.signatureVersion.toString(),
                        RECEIVED_AT_KEY to receiveTimestamp.toString(),
                    ) + additionalFields,
                vector = embeddingVector,
            )
        }
        return id
    }

    private fun String.hashId(): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            hasherFactory().digest(toByteArray(Charsets.UTF_8)),
        )

    private companion object {
        private fun validateLength(text: String) {
            if (text.length > MAX_POST_LENGTH) {
                throw BadRequestException("text must be at most $MAX_POST_LENGTH characters")
            }
        }
    }
}
