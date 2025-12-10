package straightway.mosa.client.content

import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.SearchResult

class ReplySearchService(
    private val podSessionProvider: PodSessionProvider,
    private val replyCache: ReplyCache,
    private val logger: Logger,
) {
    private val paginationTokens = mutableMapOf<String, String>()
    private val exhaustedRoots = mutableSetOf<String>()

    suspend fun fetchFor(rootId: String): ThreadReplies {
        reset(rootId)
        val snapshot = replyCache.snapshot(rootId)
        val searchResult = searchReplies(rootId) ?: return snapshot.toThreadReplies(rootId)
        val resolved = resolveReplies(searchResult)
        val updated = replyCache.append(rootId, resolved)
        updatePagination(rootId, searchResult.queryOnId, searchResult.entries)
        return updated.toThreadReplies(rootId)
    }

    suspend fun fetchMore(rootId: String): ThreadReplies? {
        if (exhaustedRoots.contains(rootId)) return null
        val queryOnId = paginationTokens[rootId] ?: return null
        val result = searchOn(queryOnId) ?: return null
        val resolved = resolveReplies(result)
        if (resolved.isEmpty()) {
            updatePagination(rootId, result.queryOnId, result.entries)
            return null
        }
        val updated = replyCache.append(rootId, resolved)
        updatePagination(rootId, result.queryOnId, result.entries)
        return updated.toThreadReplies(rootId)
    }

    private fun reset(rootId: String) {
        paginationTokens.remove(rootId)
        exhaustedRoots.remove(rootId)
    }

    private suspend fun searchReplies(rootId: String): SearchResult? =
        podSessionProvider.execute("search") { session ->
            session.podAccess.search(QueryRequest(threadRootId = rootId), session.jwt)
        }
            ?: run {
                logger.warn("Reply search failed for $rootId", null)
                null
            }

    private suspend fun searchOn(queryOnId: String): SearchResult? =
        podSessionProvider.execute("searchOn") { session ->
            session.podAccess.searchOn(QueryOnRequest(queryOnId), session.jwt)
        }
            ?: run {
                logger.warn("Reply pagination failed for $queryOnId", null)
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
        queryOnId: String,
        entries: List<SearchResult.Entry>,
    ) {
        val hasMore = queryOnId.isNotBlank() && entries.isNotEmpty()
        if (hasMore) {
            paginationTokens[rootId] = queryOnId
            exhaustedRoots.remove(rootId)
        } else {
            paginationTokens.remove(rootId)
            if (entries.isEmpty()) {
                exhaustedRoots += rootId
            }
        }
    }

    private fun ReplyCacheSnapshot.toThreadReplies(rootId: String): ThreadReplies =
        ThreadReplies(
            rootId = rootId,
            entries = entries.map { it.asQueryResultEntry() },
        )
}
