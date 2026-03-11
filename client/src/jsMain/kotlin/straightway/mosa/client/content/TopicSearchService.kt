package straightway.mosa.client.content

import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodSession
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.SearchResult

class TopicSearchService(
    private val podSessionProvider: PodSessionProvider,
    private val postCache: PostCache,
    private val contentSignatureVerifier: ContentSignatureVerifier,
    private val logger: Logger,
) {
    private val exhaustedTopics = mutableSetOf<String>()
    private val lastUsedFilters = mutableMapOf<String, Float?>()
    private val paginationScoreBounds = mutableMapOf<String, Float>()
    private val activeFromTimeBounds = mutableMapOf<String, Long?>()
    private val relaxedFilterBounds = mutableMapOf<String, Float>()
    private val pendingFilterRelaxations = mutableSetOf<String>()
    private val pendingFromTimeResets = mutableSetOf<String>()

    fun filterCleared(topic: Topic, previousFilter: Float) {
        paginationScoreBounds[topic.title] = previousFilter
        relaxedFilterBounds[topic.title] = previousFilter
        pendingFilterRelaxations += topic.title
        pendingFromTimeResets += topic.title
        exhaustedTopics.remove(topic.title)
        activeFromTimeBounds.remove(topic.title)
    }

    suspend fun fetchFor(topics: List<Topic>): List<TopicPosts> {
        if (topics.isEmpty()) return emptyList()
        topics.forEach { refreshFilterState(it) }
        val snapshots = topics.associateWith { topic -> postCache.snapshot(topic.title) }.toMutableMap()
        val searchResults = searchTopics(topics, snapshots)
        val resolvedPosts = resolvePosts(searchResults)
        searchResults.forEach { (topic, result) ->
            val snapshot = postCache.append(topic.title, resolvedPosts[topic].orEmpty())
            snapshots[topic] = snapshot
            updateExhaustion(topic, result.entries.isEmpty())
            updatePaginationBoundary(topic, result.entries)
        }
        return buildTopicPosts(topics, snapshots)
    }

    suspend fun fetchMore(topic: Topic): TopicPosts? {
        refreshFilterState(topic)
        if (exhaustedTopics.contains(topic.title)) return null
        val snapshot = postCache.snapshot(topic.title)
        val scoreBound =
            paginationScoreBounds[topic.title]
                ?: snapshot.entries.minOfOrNull { it.similarity }
                ?: return buildTopicPosts(listOf(topic), mapOf(topic to snapshot)).firstOrNull()
        val fromTime =
            if (activeFromTimeBounds.containsKey(topic.title)) {
                activeFromTimeBounds[topic.title]
            } else {
                snapshot.lastFetchedAt
            }
        val request =
            QueryRequest(
                topic = topic.title,
                fromTime = fromTime,
                scoreMustBeAbove = topic.scoreMustBeAbove,
                scoreMustBeBelow = scoreBound,
            )
        val result =
            executeRequest("search") { session ->
                session.podAccess.search(request, session.jwt)
            }
                ?: run {
                    logger.warn("Topic pagination failed for ${topic.title}", null)
                    return buildTopicPosts(listOf(topic), mapOf(topic to snapshot)).firstOrNull()
                }
        activeFromTimeBounds[topic.title] = fromTime
        if (result.entries.isEmpty()) {
            updateExhaustion(topic, true)
            updatePaginationBoundary(topic, result.entries)
            return null
        }
        val resolvedPosts = resolvePosts(mapOf(topic to result))
        val updatedSnapshot = postCache.append(topic.title, resolvedPosts[topic].orEmpty())
        updateExhaustion(topic, result.entries.isEmpty())
        updatePaginationBoundary(topic, result.entries)
        return buildTopicPosts(listOf(topic), mapOf(topic to updatedSnapshot)).firstOrNull()
    }

    private fun refreshFilterState(topic: Topic) {
        val lastFilter = lastUsedFilters[topic.title]
        if (lastFilter != topic.scoreMustBeAbove) {
            exhaustedTopics.remove(topic.title)
            lastUsedFilters[topic.title] = topic.scoreMustBeAbove
            activeFromTimeBounds.remove(topic.title)
            val previousFilter = lastFilter
            if (previousFilter != null && topic.scoreMustBeAbove == null) {
                paginationScoreBounds[topic.title] = previousFilter
                relaxedFilterBounds[topic.title] = previousFilter
                pendingFilterRelaxations += topic.title
                pendingFromTimeResets += topic.title
            } else {
                paginationScoreBounds.remove(topic.title)
                relaxedFilterBounds.remove(topic.title)
                pendingFilterRelaxations.remove(topic.title)
                pendingFromTimeResets.remove(topic.title)
            }
        }
    }

    private fun updateExhaustion(topic: Topic, responseEmpty: Boolean) {
        if (responseEmpty) {
            exhaustedTopics += topic.title
        } else {
            exhaustedTopics -= topic.title
        }
    }

    private suspend fun searchTopics(
        topics: List<Topic>,
        snapshots: Map<Topic, PostCacheSnapshot>,
    ): Map<Topic, SearchResult> {
        val results = mutableMapOf<Topic, SearchResult>()
        topics.forEach { topic ->
            val cachedSnapshot = snapshots.getValue(topic)
            val relaxationPending = pendingFilterRelaxations.contains(topic.title)
            val relaxed =
                if (relaxationPending) {
                    relaxedFilterBounds[topic.title] ?: paginationScoreBounds[topic.title]
                } else {
                    null
                }
            val resetFromTime = pendingFromTimeResets.contains(topic.title)
            val fromTime = if (resetFromTime || relaxationPending) null else cachedSnapshot.lastFetchedAt
            val queryRequest =
                QueryRequest(
                    topic = topic.title,
                    fromTime = fromTime,
                    scoreMustBeAbove = topic.scoreMustBeAbove,
                    scoreMustBeBelow = relaxed,
                )
            val result =
                executeRequest("search") { session ->
                    session.podAccess.search(queryRequest, session.jwt)
                }
                    ?: run {
                        logger.warn("Topic search failed for ${topic.title}", null)
                        return@forEach
                    }
            if (relaxationPending) {
                pendingFilterRelaxations.remove(topic.title)
                relaxedFilterBounds.remove(topic.title)
            }
            if (resetFromTime) {
                pendingFromTimeResets.remove(topic.title)
            }
            activeFromTimeBounds[topic.title] = fromTime
            results[topic] = result
        }
        return results
    }

    private suspend fun resolvePosts(
        searchResults: Map<Topic, SearchResult>,
    ): Map<Topic, List<TopicScopedPost>> {
        if (searchResults.isEmpty()) return emptyMap()
        val allIds =
            searchResults
                .values
                .flatMap { result -> result.entries.map { it.id } }
        val cacheResolution = postCache.resolve(allIds)
        val fetched = fetchPosts(cacheResolution.missingIds.distinct())
        val postsById =
            cacheResolution.postsById + fetched.associateBy { it.id }
        return searchResults.mapValues { (_, result) ->
            result.entries.mapNotNull { entry ->
                postsById[entry.id]?.let { post -> TopicScopedPost(post, entry.similarity) }
            }
        }
    }

    private suspend fun fetchPosts(ids: List<String>): List<CachedPost> {
        if (ids.isEmpty()) return emptyList()
        val response =
            executeRequest("fetch") { session ->
                session.podAccess.fetch(FetchRequest(ids), session.jwt)
            }
                ?: run {
                    logger.warn("Post fetch failed for ${ids.size} ids", null)
                    return emptyList()
                }
        return response.entries.mapNotNull { it.toVerifiedCachedPostOrNull() }
    }

    private fun FetchResult.Entry.toCachedPost(): CachedPost =
        CachedPost(
            id = id,
            content = content,
            authorGID = authorGID,
            storedAt = storedAt,
            rootId = rootId,
            authorPublicKey = authorPublicKey,
            authorSignature = authorSignature,
            signatureVersion = signatureVersion,
        )

    private suspend fun FetchResult.Entry.toVerifiedCachedPostOrNull(): CachedPost? {
        val result = contentSignatureVerifier.verify(this)
        if (result is ContentVerificationResult.Invalid) {
            logger.warn("Discarded invalid fetched post $id: ${result.reason}", null)
            return null
        }
        return toCachedPost()
    }

    private fun updatePaginationBoundary(topic: Topic, entries: List<SearchResult.Entry>) {
        val lowestScore = entries.minOfOrNull { it.similarity }
        if (lowestScore != null) {
            paginationScoreBounds[topic.title] = lowestScore
        } else {
            paginationScoreBounds.remove(topic.title)
        }
    }

    private fun buildTopicPosts(
        topics: List<Topic>,
        snapshots: Map<Topic, PostCacheSnapshot>,
    ): List<TopicPosts> =
        topics.mapNotNull { topic ->
            val entries =
                snapshots[topic]
                    ?.entries
                    ?.map { it.asQueryResultEntry() }
                    .orEmpty()
            entries.takeIf { it.isNotEmpty() }?.let { TopicPosts(topic, it) }
        }

    private suspend fun <T> executeRequest(
        name: String,
        call: suspend (PodSession) -> T,
    ): T? = podSessionProvider.execute(name, call)
}
