package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY

internal class ContentDeleteService(
    private val vectorStore: VectorStore,
    private val isAdmin: suspend (String) -> Boolean,
) {
    suspend fun loadAllForUser(userGID: String): List<VectorStore.PostDump> {
        val ownPosts = vectorStore.fetchByFieldValues(AUTHOR_GID_KEY, setOf(userGID))
        val rootIds = ownPosts.filter { it.fields[ROOT_ID_KEY] == null }.mapTo(linkedSetOf(), VectorStore.PostDump::id)
        val linkedReplies =
            if (rootIds.isEmpty()) {
                emptyList()
            } else {
                vectorStore.fetchByFieldValues(ROOT_ID_KEY, rootIds)
            }
        return (ownPosts + linkedReplies).distinctBy(VectorStore.PostDump::id)
    }

    suspend fun delete(
        userGID: String,
        id: String,
    ): List<String> {
        if (id.isBlank()) throw BadRequestException("id must not be blank")
        val post = vectorStore.fetch(listOf(id)).singleOrNull() ?: throw ContentService.ContentNotFoundException()
        val author = post.fields.requireContentMetadata(post.id, ContentMetadataField.AuthorGid)
        if (author != userGID && !isAdmin(userGID)) throw ContentService.ContentForbiddenException()
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
