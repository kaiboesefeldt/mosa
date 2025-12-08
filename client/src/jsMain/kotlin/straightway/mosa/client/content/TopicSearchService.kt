package straightway.mosa.client.content

import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.QueryRequest

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

    suspend fun clearCache(topic: Topic) {
        runCatching { postCache.clear(topic.title) }
            .onFailure { logger.warn("Topic cache clear failed for ${topic.title}", it) }
        exhaustedTopics.remove(topic.title)
        lastUsedFilters.remove(topic.title)
    }

    suspend fun fetchFor(topics: List<Topic>): List<TopicPosts> {
        if (topics.isEmpty()) return emptyList()
        topics.forEach { refreshFilterState(it) }
        val snapshots =
            topics
                .associateWith { topic -> postCache.snapshot(topic.title) }
                .toMutableMap()
        val topicsNeedingQuery = topics.filter { topic -> snapshots.getValue(topic).entries.isEmpty() }
        if (topicsNeedingQuery.isEmpty()) {
            return buildTopicPosts(topics, snapshots)
        }
        val session = preparePodSession() ?: return buildTopicPosts(topics, snapshots)
        topicsNeedingQuery.forEach { topic ->
            val cachedSnapshot = snapshots.getValue(topic)
            val snapshot =
                runCatching {
                    val queryRequest =
                            QueryRequest(
                                topic = topic.title,
                                scoreMustBeAbove = topic.scoreMustBeAbove,
                                minTime = cachedSnapshot.lastFetchedAt,
                            )
                    session.podAccess.query(queryRequest, session.jwt)
                }
                    .onFailure { logger.warn("Topic query failed for ${topic.title}", it) }
                    .getOrNull()
                    ?.let {
                        updateExhaustion(topic, it.entries.isEmpty())
                        postCache.append(topic.title, it.entries)
                    }
                    ?: cachedSnapshot
            snapshots[topic] = snapshot
        }
        return buildTopicPosts(topics, snapshots)
    }

    suspend fun fetchMore(topic: Topic): TopicPosts? {
        refreshFilterState(topic)
        if (exhaustedTopics.contains(topic.title)) return null
        val snapshot = postCache.snapshot(topic.title)
        val eligibleEntries =
            snapshot.entries.filter { entry ->
                topic.scoreMustBeAbove?.let { entry.similarity > it } ?: true
            }
        val lastEntry = eligibleEntries.lastOrNull() ?: return null
        val session = preparePodSession() ?: return buildTopicPosts(listOf(topic), mapOf(topic to snapshot)).firstOrNull()
        val result =
            runCatching {
                val queryRequest =
                    QueryRequest(
                        topic = topic.title,
                        scoreMustBeAbove = topic.scoreMustBeAbove,
                        scoreMustBeBelow = lastEntry.similarity,
                    )
                session.podAccess.query(queryRequest, session.jwt)
            }
                .onFailure { logger.warn("Topic pagination failed for ${topic.title}", it) }
                .getOrNull()
                ?: return buildTopicPosts(listOf(topic), mapOf(topic to snapshot)).firstOrNull()
        val updatedSnapshot = postCache.append(topic.title, result.entries)
        updateExhaustion(topic, result.entries.isEmpty())
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
        }
    }

    private fun updateExhaustion(topic: Topic, responseEmpty: Boolean) {
        if (responseEmpty) {
            exhaustedTopics += topic.title
        } else {
            exhaustedTopics -= topic.title
        }
    }

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
