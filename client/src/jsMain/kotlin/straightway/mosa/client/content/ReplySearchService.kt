package straightway.mosa.client.content

import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.SearchResult

class ReplySearchService(
    private val podSessionProvider: PodSessionProvider,
    private val replyCache: ReplyCache,
    private val logger: Logger,
) {
    private val paginationBounds = mutableMapOf<String, Long>()
    private val exhaustedRoots = mutableSetOf<String>()

    suspend fun fetchFor(rootId: String): ThreadReplies {
        reset(rootId)
        val snapshot = replyCache.snapshot(rootId)
        val searchResult = searchReplies(rootId, null) ?: return snapshot.toThreadReplies(rootId)
        val resolved = resolveReplies(searchResult)
        val updated = replyCache.append(rootId, resolved)
        updatePagination(rootId, updated.entries, searchResult.entries.isNotEmpty())
        return updated.toThreadReplies(rootId)
    }

    suspend fun fetchMore(rootId: String): ThreadReplies? {
        if (exhaustedRoots.contains(rootId)) return null
        val timeTo = paginationBounds[rootId] ?: return null
        val result = searchReplies(rootId, timeTo) ?: return null
        val resolved = resolveReplies(result)
        val updated =
            if (resolved.isEmpty()) {
                replyCache.snapshot(rootId)
            } else {
                replyCache.append(rootId, resolved)
            }
        updatePagination(rootId, updated.entries, result.entries.isNotEmpty())
        if (resolved.isEmpty()) return null
        return updated.toThreadReplies(rootId)
    }

    private fun reset(rootId: String) {
        paginationBounds.remove(rootId)
        exhaustedRoots.remove(rootId)
    }

    private suspend fun searchReplies(
        rootId: String,
        timeTo: Long?,
    ): SearchResult? =
        podSessionProvider.execute("search") { session ->
            session.podAccess.search(
                QueryRequest(
                    threadRootId = rootId,
                    toTime = timeTo,
                ),
                session.jwt,
            )
        }
            ?: run {
                logger.warn("Reply search failed for $rootId", null)
                null
            }

    private suspend fun resolveReplies(result: SearchResult): List<ThreadScopedReply> {
        if (result.entries.isEmpty()) return emptyList()
        val ids = result.entries.map { it.id }
        val cacheResolution = replyCache.resolve(ids)
        val fetched = fetchPosts(cacheResolution.missingIds)
        val postsById = cacheResolution.postsById + fetched.associateBy { it.id }
        return result.entries.mapNotNull { entry ->
            postsById[entry.id]?.let { post -> ThreadScopedReply(post) }
        }
    }

    private suspend fun fetchPosts(ids: List<String>): List<CachedPost> {
        if (ids.isEmpty()) return emptyList()
        return podSessionProvider.execute("fetch") { session ->
            session.podAccess.fetch(FetchRequest(ids), session.jwt)
        }
            ?.entries
            ?.map { it.toCachedPost() }
            .orEmpty()
            .also {
                if (it.isEmpty()) {
                    logger.warn("Reply fetch failed for ${ids.size} ids", null)
                }
            }
    }

    private fun FetchResult.Entry.toCachedPost(): CachedPost =
        CachedPost(
            id = id,
            content = content,
            authorGID = authorGID,
            storedAt = storedAt,
            rootId = rootId,
        )

    private fun updatePagination(
        rootId: String,
        entries: List<ThreadScopedReply>,
        hasMore: Boolean,
    ) {
        val oldest = entries.minOfOrNull { it.post.storedAt }
        if (hasMore && oldest != null) {
            paginationBounds[rootId] = (oldest - 1).coerceAtLeast(0)
            exhaustedRoots.remove(rootId)
        } else {
            paginationBounds.remove(rootId)
            exhaustedRoots += rootId
        }
    }

    private fun ReplyCacheSnapshot.toThreadReplies(rootId: String): ThreadReplies =
        ThreadReplies(
            rootId = rootId,
            entries = entries.map { it.asQueryResultEntry() },
        )
}
