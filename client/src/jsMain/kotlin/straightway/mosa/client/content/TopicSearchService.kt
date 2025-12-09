package straightway.mosa.client.content

import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.SearchResult

class TopicSearchService(
    private val identityRepository: IdentitySelectionRepository,
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val jwtBuilder: JwtBuilder,
    private val postCache: PostCache,
    private val logger: Logger,
) {
    private val exhaustedTopics = mutableSetOf<String>()
    private val lastUsedFilters = mutableMapOf<String, Float?>()
    private val paginationScoreBounds = mutableMapOf<String, Float>()
    private val activeMinTimes = mutableMapOf<String, Long?>()
    private val relaxedFilterBounds = mutableMapOf<String, Float>()
    private val pendingFilterRelaxations = mutableSetOf<String>()
    private val pendingMinResets = mutableSetOf<String>()

    fun filterCleared(topic: Topic, previousFilter: Float) {
        paginationScoreBounds[topic.title] = previousFilter
        relaxedFilterBounds[topic.title] = previousFilter
        pendingFilterRelaxations += topic.title
        pendingMinResets += topic.title
        exhaustedTopics.remove(topic.title)
        activeMinTimes.remove(topic.title)
    }

    suspend fun fetchFor(topics: List<Topic>): List<TopicPosts> {
        if (topics.isEmpty()) return emptyList()
        topics.forEach { refreshFilterState(it) }
        val snapshots = topics.associateWith { topic -> postCache.snapshot(topic.title) }.toMutableMap()
        val session = preparePodSession() ?: return buildTopicPosts(topics, snapshots)
        val searchResults = searchTopics(session, topics, snapshots)
        val resolvedPosts = resolvePosts(session, searchResults)
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
        val session = preparePodSession() ?: return buildTopicPosts(listOf(topic), mapOf(topic to snapshot)).firstOrNull()
        val scoreBound =
            paginationScoreBounds[topic.title]
                ?: snapshot.entries.minOfOrNull { it.similarity }
                ?: return buildTopicPosts(listOf(topic), mapOf(topic to snapshot)).firstOrNull()
        val minTime =
            if (activeMinTimes.containsKey(topic.title)) {
                activeMinTimes[topic.title]
            } else {
                snapshot.lastFetchedAt
            }
        activeMinTimes[topic.title] = minTime
        val request =
            QueryRequest(
                topic = topic.title,
                scoreMustBeAbove = topic.scoreMustBeAbove,
                scoreMustBeBelow = scoreBound,
                minTime = minTime,
            )
        val result =
            runCatching {
                session.podAccess.search(request, session.jwt)
            }
                .onFailure { logger.warn("Topic pagination failed for ${topic.title}", it) }
                .getOrNull()
                ?: return buildTopicPosts(listOf(topic), mapOf(topic to snapshot)).firstOrNull()
        if (result.entries.isEmpty()) {
            updateExhaustion(topic, true)
            updatePaginationBoundary(topic, result.entries)
            return null
        }
        val resolvedPosts = resolvePosts(session, mapOf(topic to result))
        val updatedSnapshot = postCache.append(topic.title, resolvedPosts[topic].orEmpty())
        updateExhaustion(topic, result.entries.isEmpty())
        updatePaginationBoundary(topic, result.entries)
        return buildTopicPosts(listOf(topic), mapOf(topic to updatedSnapshot)).firstOrNull()
    }

    private suspend fun loadIdentity(): Identity? =
        runCatching { identityRepository.loadOrCreate().identity }
            .getOrElse {
                logger.error("Failed to load identity", it)
                null
            }

    private fun refreshFilterState(topic: Topic) {
        val lastFilter = lastUsedFilters[topic.title]
        if (lastFilter != topic.scoreMustBeAbove) {
            exhaustedTopics.remove(topic.title)
            lastUsedFilters[topic.title] = topic.scoreMustBeAbove
            activeMinTimes.remove(topic.title)
            val previousFilter = lastFilter
            if (previousFilter != null && topic.scoreMustBeAbove == null) {
                paginationScoreBounds[topic.title] = previousFilter
                relaxedFilterBounds[topic.title] = previousFilter
                pendingFilterRelaxations += topic.title
                pendingMinResets += topic.title
            } else {
                paginationScoreBounds.remove(topic.title)
                relaxedFilterBounds.remove(topic.title)
                pendingFilterRelaxations.remove(topic.title)
                pendingMinResets.remove(topic.title)
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
        session: PodSession,
        topics: List<Topic>,
        snapshots: Map<Topic, PostCacheSnapshot>,
    ): Map<Topic, SearchResult> {
        val results = mutableMapOf<Topic, SearchResult>()
        topics.forEach { topic ->
            val cachedSnapshot = snapshots.getValue(topic)
            val relaxed =
                if (pendingFilterRelaxations.remove(topic.title)) {
                    relaxedFilterBounds.remove(topic.title) ?: paginationScoreBounds[topic.title]
                } else {
                    null
                }
            val resetMinTime = pendingMinResets.remove(topic.title)
            val minTime = if (resetMinTime || relaxed != null) null else cachedSnapshot.lastFetchedAt
            activeMinTimes[topic.title] = minTime
            val queryRequest =
                QueryRequest(
                    topic = topic.title,
                    scoreMustBeAbove = topic.scoreMustBeAbove,
                    scoreMustBeBelow = relaxed,
                    minTime = minTime,
                )
            val result =
                runCatching { session.podAccess.search(queryRequest, session.jwt) }
                    .onFailure { logger.warn("Topic search failed for ${topic.title}", it) }
                    .getOrNull()
            if (result != null) {
                results[topic] = result
            }
        }
        return results
    }

    private suspend fun resolvePosts(
        session: PodSession,
        searchResults: Map<Topic, SearchResult>,
    ): Map<Topic, List<TopicScopedPost>> {
        if (searchResults.isEmpty()) return emptyMap()
        val allIds =
            searchResults
                .values
                .flatMap { result -> result.entries.map { it.id } }
        val cacheResolution = postCache.resolve(allIds)
        val fetched = fetchPosts(session, cacheResolution.missingIds.distinct())
        val postsById =
            cacheResolution.postsById + fetched.associateBy { it.id }
        return searchResults.mapValues { (_, result) ->
            result.entries.mapNotNull { entry ->
                postsById[entry.id]?.let { post -> TopicScopedPost(post, entry.similarity) }
            }
        }
    }

    private suspend fun fetchPosts(
        session: PodSession,
        ids: List<String>,
    ): List<CachedPost> {
        if (ids.isEmpty()) return emptyList()
        return runCatching { session.podAccess.fetch(FetchRequest(ids), session.jwt) }
            .onFailure { logger.warn("Post fetch failed for ${ids.size} ids", it) }
            .getOrNull()
            ?.entries
            ?.map { it.toCachedPost() }
            .orEmpty()
    }

    private fun updatePaginationBoundary(topic: Topic, entries: List<SearchResult.Entry>) {
        val lowestScore = entries.minOfOrNull { it.similarity }
        if (lowestScore != null) {
            paginationScoreBounds[topic.title] = lowestScore
        } else {
            paginationScoreBounds.remove(topic.title)
        }
    }

    private fun FetchResult.Entry.toCachedPost(): CachedPost =
        CachedPost(id = id, content = content, authorGID = authorGID, storedAt = storedAt, rootId = rootId)

    private data class PodSession(val podAccess: PodAccess, val jwt: String)

    private suspend fun preparePodSession(): PodSession? {
        val podBase = podBaseProvider.base() ?: return null
        val identity = loadIdentity() ?: return null
        val podAccess = runCatching { podAccessFactory(podBase) }.getOrElse {
            logger.error("Pod access unavailable", it)
            return null
        }
        val certificate = runCatching { podAccess.approveGid(identity.gid) }.getOrElse {
            logger.error("Pod certificate retrieval failed", it)
            return null
        }
        val jwt = runCatching { jwtBuilder(identity, certificate) }.getOrElse {
            logger.error("JWT creation failed", it)
            return null
        }
        return PodSession(podAccess, jwt)
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
}
