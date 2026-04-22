package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import straightway.mosa.VectorStore
import straightway.mosa.content.complaint.PostComplaintRecord
import straightway.mosa.content.complaint.PostComplaintStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY

internal class ContentDeleteService(
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
            deletedIds.addAll(collectReplyIds(post.id))
        }
        val complaintsByPostId = deletedIds.associateWith { postComplaintStore.loadForPost(it) }
        try {
            postComplaintStore.deleteByIds(complaintsByPostId.values.flatten().map(PostComplaintRecord::id))
            vectorStore.writeAccess {
                deletedIds.forEach { remove(it) }
            }
        } catch (exception: Throwable) {
            complaintsByPostId.values.flatten().forEach { postComplaintStore.restore(it) }
            throw exception
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
            val complaintsByPostId = queryResult.resultsPage.associate { it.id to postComplaintStore.loadForPost(it.id) }
            try {
                removeQueryResults(queryResult, deletedIds)
                queryResult.resultsPage.forEach { postComplaintStore.deleteForPost(it.id) }
                rootIds.forEach { deleteRepliesForRoot(it, deletedIds) }
            } catch (exception: Throwable) {
                complaintsByPostId.values.flatten().forEach { postComplaintStore.restore(it) }
                throw exception
            }
        }
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
            val complaintsByPostId = replies.resultsPage.associate { it.id to postComplaintStore.loadForPost(it.id) }
            try {
                removeQueryResults(replies, deletedIds)
                replies.resultsPage.forEach { postComplaintStore.deleteForPost(it.id) }
            } catch (exception: Throwable) {
                complaintsByPostId.values.flatten().forEach { postComplaintStore.restore(it) }
                throw exception
            }
        }
    }

    private suspend fun collectReplyIds(rootId: String): List<String> {
        val replyIds = mutableListOf<String>()
        var replies =
            vectorStore.query(
                VectorStore.Query(fields = mapOf(ROOT_ID_KEY to rootId)),
            )
        while (true) {
            replyIds.addAll(replies.resultsPage.map { it.id })
            val queryOnId = replies.queryOnId ?: return replyIds
            replies = vectorStore.queryOn(queryOnId)
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
