package straightway.mosa.client.content

import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
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
    suspend fun clearCache(topic: Topic) {
        runCatching { postCache.clear(topic.title) }
            .onFailure { logger.warn("Topic cache clear failed for ${topic.title}", it) }
    }

    suspend fun fetchFor(topics: List<Topic>): List<TopicPosts> {
        if (topics.isEmpty()) return emptyList()
        val snapshots =
            topics
                .associateWith { topic -> postCache.snapshot(topic.title) }
                .toMutableMap()
        val topicsNeedingQuery = topics.filter { topic -> snapshots.getValue(topic).entries.isEmpty() }
        if (topicsNeedingQuery.isEmpty()) {
            return buildTopicPosts(topics, snapshots)
        }
        val podBase = podBaseProvider.base() ?: return buildTopicPosts(topics, snapshots)
        val identity = loadIdentity() ?: return buildTopicPosts(topics, snapshots)
        val podAccess = runCatching { podAccessFactory(podBase) }.getOrElse {
            logger.error("Pod access unavailable", it)
            return buildTopicPosts(topics, snapshots)
        }
        val certificate = runCatching { podAccess.approveGid(identity.gid) }.getOrElse {
            logger.error("Pod certificate retrieval failed", it)
            return buildTopicPosts(topics, snapshots)
        }
        val jwt = runCatching { jwtBuilder(identity, certificate) }.getOrElse {
            logger.error("JWT creation failed", it)
            return buildTopicPosts(topics, snapshots)
        }
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
                    podAccess.query(queryRequest, jwt)
                }
                    .onFailure { logger.warn("Topic query failed for ${topic.title}", it) }
                    .getOrNull()
                    ?.let { postCache.append(topic.title, it.entries) }
                    ?: cachedSnapshot
            snapshots[topic] = snapshot
        }
        return buildTopicPosts(topics, snapshots)
    }

    private suspend fun loadIdentity(): Identity? =
        runCatching { identityRepository.loadOrCreate().identity }
            .getOrElse {
                logger.error("Failed to load identity", it)
                null
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
