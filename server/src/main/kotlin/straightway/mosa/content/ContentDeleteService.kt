package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.content.complaint.PostComplaintRecord
import straightway.mosa.content.complaint.PostComplaintStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY

internal class ContentDeleteService(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val postComplaintStore: PostComplaintStore,
    private val isAdmin: suspend (String) -> Boolean,
) {
    suspend fun delete(
        userGID: String,
        id: String,
    ): List<String> {
        if (id.isBlank()) throw BadRequestException("id must not be blank")
        val post = vectorStore.fetch(listOf(id)).singleOrNull() ?: throw ContentService.ContentNotFoundException()
        val author = post.fields.requireContentMetadata(post.id, ContentMetadataField.AuthorGid)
        if (author != userGID && !isAdmin(userGID)) throw ContentService.ContentForbiddenException()
        val deletedIds = linkedSetOf(post.id)
        if (post.fields[ROOT_ID_KEY] == null) {
            collectReplyIds(post.id, deletedIds)
        }
        val deletedPosts = vectorStore.fetch(deletedIds.toList())
        val complaintsByPostId = deletedIds.associateWith { postComplaintStore.loadForPost(it) }
        try {
            postComplaintStore.deleteByIds(complaintsByPostId.values.flatten().map(PostComplaintRecord::id))
            vectorStore.writeAccess {
                deletedIds.forEach { remove(it) }
            }
        } catch (exception: Throwable) {
            rollbackDeletion(deletedPosts, complaintsByPostId, exception)
            throw exception
        }
        return deletedIds.toList()
    }

    suspend fun deleteAllForUser(userGID: String): List<String> {
        val deletedIds = collectAllUserPostIds(userGID)
        if (deletedIds.isEmpty()) return emptyList()
        val deletedPosts = vectorStore.fetch(deletedIds.toList())
        val complaintsByPostId = deletedIds.associateWith { postComplaintStore.loadForPost(it) }
        try {
            deletedIds.forEach { postComplaintStore.deleteForPost(it) }
            vectorStore.writeAccess {
                deletedIds.forEach { remove(it) }
            }
        } catch (exception: Throwable) {
            rollbackDeletion(deletedPosts, complaintsByPostId, exception)
            throw exception
        }
        return deletedIds.toList()
    }

    private suspend fun collectAllUserPostIds(userGID: String): LinkedHashSet<String> {
        val deletedIds = linkedSetOf<String>()
        var queryResult =
            vectorStore.query(
                VectorStore.Query(fields = mapOf(AUTHOR_GID_KEY to userGID)),
            )
        while (true) {
            if (queryResult.resultsPage.isEmpty()) return deletedIds
            val postIds = queryResult.resultsPage.map { it.id }
            deletedIds.addAll(postIds)
            postIds.forEach { collectReplyIds(it, deletedIds) }
            val queryOnId = queryResult.queryOnId ?: return deletedIds
            queryResult = vectorStore.queryOn(queryOnId)
        }
    }

    private suspend fun collectReplyIds(
        rootId: String,
        deletedIds: MutableSet<String>,
        visitedRoots: MutableSet<String> = linkedSetOf(),
    ) {
        if (!visitedRoots.add(rootId)) return
        var replies =
            vectorStore.query(
                VectorStore.Query(fields = mapOf(ROOT_ID_KEY to rootId)),
            )
        while (true) {
            val replyIds = replies.resultsPage.map { it.id }
            deletedIds.addAll(replyIds)
            replyIds.forEach { collectReplyIds(it, deletedIds, visitedRoots) }
            val queryOnId = replies.queryOnId ?: return
            replies = vectorStore.queryOn(queryOnId)
        }
    }

    private suspend fun rollbackDeletion(
        deletedPosts: List<VectorStore.PostDump>,
        complaintsByPostId: Map<String, List<PostComplaintRecord>>,
        originalException: Throwable,
    ) {
        runCatching { restorePosts(deletedPosts) }
            .onFailure { originalException.addSuppressed(it) }
        runCatching {
            complaintsByPostId.values.flatten().forEach { postComplaintStore.restore(it) }
        }.onFailure { originalException.addSuppressed(it) }
    }

    private suspend fun restorePosts(posts: List<VectorStore.PostDump>) {
        if (posts.isEmpty()) return
        vectorStore.writeAccess {
            posts.forEach { post ->
                add(
                    id = post.id,
                    text = post.text,
                    fields = post.fields,
                    vector = embeddingService.embed(post.text),
                )
            }
        }
    }
}
