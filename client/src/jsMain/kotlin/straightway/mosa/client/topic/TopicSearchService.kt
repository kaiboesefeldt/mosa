package straightway.mosa.client.topic

import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult

typealias PodAccessFactory = (String) -> PodAccess
typealias JwtBuilder = suspend (Identity, PodCertificate) -> String

data class TopicPosts(
    val topic: Topic,
    val entries: List<QueryResult.Entry>,
)

class TopicSearchService(
    private val identityRepository: IdentitySelectionRepository,
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val jwtBuilder: JwtBuilder,
    private val logger: Logger,
) {
    suspend fun fetchFor(topics: List<Topic>): List<TopicPosts> {
        if (topics.isEmpty()) return emptyList()
        val podBase = podBaseProvider.base() ?: return emptyList()
        val identity = loadIdentity() ?: return emptyList()
        val podAccess = runCatching { podAccessFactory(podBase) }.getOrElse {
            logger.error("Pod access unavailable", it)
            return emptyList()
        }
        val certificate = runCatching { podAccess.approveGid(identity.gid) }.getOrElse {
            logger.error("Pod certificate retrieval failed", it)
            return emptyList()
        }
        val jwt = runCatching { jwtBuilder(identity, certificate) }.getOrElse {
            logger.error("JWT creation failed", it)
            return emptyList()
        }
        return topics.mapNotNull { topic ->
            runCatching { podAccess.query(QueryRequest(topic.title), jwt) }
                .onFailure { logger.warn("Topic query failed for ${topic.title}", it) }
                .getOrNull()
                ?.entries
                ?.takeIf { it.isNotEmpty() }
                ?.let { TopicPosts(topic, it) }
        }
    }

    private suspend fun loadIdentity(): Identity? =
        runCatching { identityRepository.loadOrCreate().identity }
            .getOrElse {
                logger.error("Failed to load identity", it)
                null
            }
}
