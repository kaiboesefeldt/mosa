package straightway.mosa.content

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.testdata.TestPublish
import straightway.mosa.vectorstore.LuceneIndexAccess
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import straightway.mosa.vectorstore.LuceneQueryPlanner
import straightway.mosa.vectorstore.QueryOnRegistry
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

@EnabledIfEnvironmentVariable(named = "MOSA_RUN_QUALITY_TEST", matches = "true")
class QualityTest {
    val embeddingService = OnnxLocalEmbeddingService.create()
    val clock: Clock =
        Clock.fixed(
            Instant.ofEpochSecond(170000000L),
            ZoneId.of("UTC"),
        )
    val indexAccess = LuceneIndexAccess { ByteBuffersDirectory() }
    val vectorStore =
        LuceneLocalVectorStore.create(
            indexAccess = indexAccess,
            paginationRegistry = QueryOnRegistry(pageSize = 10, maxNotFinishedQueries = 10000),
            queryPlanner = LuceneQueryPlanner(maxResults = 1000),
            clock,
            vectorSize = embeddingService.dimension,
        )
    val contentService =
        ContentService(
            embeddingService,
            vectorStore,
            { MessageDigest.getInstance("SHA-256") },
            maxFetchItems = 100,
            postComplaintStore = mockk(relaxed = true),
        )
    val gid = "gid"

    val published: Map<String, TestPublish>
    val searchable: List<TestPublish>

    init {
        val allPublished = TestPublish.load()
        val topics = allPublished.keys
        searchable =
            topics.flatMap { topic -> allPublished[topic]!!.take(10) }
        published =
            topics
                .flatMap { topic -> allPublished[topic]!!.drop(10) }
                .associateBy { it.text }

        measureTime {
            runTest(timeout = 10.minutes) {
                print(".")
                System.out.flush()
                published.values.forEach {
                    contentService.publish(gid, PublishRequest(it.text), 0L, "")
                }
            }
        }.also {
            println("Time for ingesting ${published.size} documents: $it")
        }
    }

    @AfterTest
    fun afterTest() {
        indexAccess.close()
    }

    @Test
    fun testQuality() {
        data class TopicResult(
            val onTopic: Int,
            val offTopic: Int,
            val firstOffTopic: Int,
            val searches: Int,
        )

        val targetSize = 10
        val topicResults = mutableMapOf<String, TopicResult>()
        measureTime {
            searchable.forEach { s ->
                val overallQueryResult = mutableListOf<QueryResult.Entry>()
                runTest {
                    val initialQueryResult =
                        contentService.query(
                            QueryRequest(topic = s.text),
                        )
                    overallQueryResult.addAll(initialQueryResult.entries)
                    var searchOnId = initialQueryResult.queryOnId
                    while (overallQueryResult.size < targetSize && searchOnId.isNotEmpty()) {
                        val queryOnResult =
                            contentService.queryOn(
                                QueryOnRequest(searchOnId),
                            )
                        overallQueryResult.addAll(queryOnResult.entries)
                        searchOnId = queryOnResult.queryOnId
                    }

                    val currTopicResult =
                        topicResults[s.topic] ?: TopicResult(
                            onTopic = 0,
                            offTopic = 0,
                            firstOffTopic = 0,
                            searches = 0,
                        )
                    val onTopicResults =
                        overallQueryResult.count { published[it.content]?.topic == s.topic }
                    val offTopicResults =
                        overallQueryResult.count { published[it.content]?.topic != s.topic }
                    val firstOffTopicResult =
                        overallQueryResult
                            .indexOfFirst {
                                published[it.content]?.topic != s.topic
                            }.let { if (it < 0) overallQueryResult.size else it }
                    topicResults[s.topic] =
                        currTopicResult.copy(
                            onTopic = onTopicResults + currTopicResult.onTopic,
                            offTopic = offTopicResults + currTopicResult.offTopic,
                            firstOffTopic = firstOffTopicResult + currTopicResult.firstOffTopic,
                            searches = currTopicResult.searches + 1,
                        )
                }
            }
        }.also {
            println("Time for querying ${searchable.size} x $targetSize = ${searchable.size * targetSize} documents: $it")
        }

        var overallOnTopic = 0f
        var overallResults = 0f
        var firstOffTopic = 0f
        var searches = 0
        topicResults.keys.sorted().forEach {
            val topicResults = topicResults[it]!!
            println("$it: $topicResults")
            overallOnTopic += topicResults.onTopic
            overallResults += topicResults.onTopic + topicResults.offTopic
            firstOffTopic += topicResults.firstOffTopic
            searches += topicResults.searches
        }
        val hitRate = overallOnTopic / overallResults
        println("Hit rate: $hitRate")
        val firstOffTopicAvg = firstOffTopic / searches
        println("First off-topic: $firstOffTopicAvg")

        assertTrue(0.85 < hitRate, "Hit rate too low")
        assertTrue(6.5 < firstOffTopicAvg, "First off-topic hit too early")
    }
}
