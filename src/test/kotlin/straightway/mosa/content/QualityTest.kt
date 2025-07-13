package straightway.mosa.content

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Test
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min

class QualityTest {
    val embeddingService = OnnxLocalEmbeddingService()
    val vectorStore =
        LuceneLocalVectorStore(
            directoryFactory = { ByteBuffersDirectory() },
            maxResults = 1000,
            pageSize = 10,
            maxNotFinishedQueries = 10000,
            vectorSize = embeddingService.dimension,
        )
    val contentService =
        ContentService(
            embeddingService,
            vectorStore,
        ) { MessageDigest.getInstance("SHA-256") }
    val gid = "gid"

    data class TestPublish(
        val topic: String,
        val lang: Locale,
        val text: String,
    )

    val published: Map<String, TestPublish>
    val searchable: List<TestPublish>

    init {
        val resourceUrl = javaClass.classLoader.getResource("testContent")
        requireNotNull(resourceUrl) { "Resource directory not found!" }
        val path = Paths.get(resourceUrl.toURI())
        val mapper = jacksonObjectMapper()
        var published = listOf<TestPublish>()
        var searchable = listOf<TestPublish>()
        processResourceFilesInPath(path) { fileContent ->
            val publishObjects = mapper.readValue(fileContent, object : TypeReference<List<TestPublish>>() {})
            searchable = searchable + publishObjects.take(10)
            published = published + publishObjects.drop(10)
        }
        this.published = published.associate { it.text to it }
        this.searchable = searchable
        runBlocking {
            published.forEach {
                contentService.publish(gid, PublishRequest(it.text))
            }
        }
    }

    @Test
    fun testQuality() {
        data class TopicResult(
            val onTopic: Int,
            val offTopic: Int,
            val firstOffTopic: Int,
        )

        val targetSize = 10
        val topicResults = mutableMapOf<String, TopicResult>()
        searchable.forEach { s ->
            val overallQueryResult = mutableListOf<QueryResult.Entry>()
            runBlocking {
                val embeddingVector = embeddingService.embed(s.text)
                val initialQueryResult =
                    contentService.query(
                        gid,
                        QueryRequest(
                            embeddingVersion = "1",
                            embeddingVector = embeddingVector.toList(),
                            maxResults = 10,
                        ),
                    )
                overallQueryResult.addAll(initialQueryResult.entries)
                var searchOnId = initialQueryResult.queryOnId
                while (overallQueryResult.size < targetSize && searchOnId.isNotEmpty()) {
                    val queryOnResult =
                        contentService.queryOn(
                            gid,
                            QueryOnRequest(
                                searchOnId,
                                maxResults = 10,
                            ),
                        )
                    overallQueryResult.addAll(queryOnResult.entries)
                    searchOnId = queryOnResult.queryOnId
                }

                val currTopicResult =
                    topicResults[s.topic] ?: TopicResult(
                        onTopic = 0,
                        offTopic = 0,
                        firstOffTopic = Int.MAX_VALUE,
                    )
                val onTopicResults =
                    overallQueryResult.count { published[it.content]?.topic == s.topic }
                val offTopicResults =
                    overallQueryResult.count { published[it.content]?.topic != s.topic }
                val firstOffTopicResult =
                    overallQueryResult
                        .indexOfFirst {
                            published[it.content]?.topic == s.topic
                        }.let { if (it < 0) Int.MAX_VALUE else it }
                topicResults[s.topic] =
                    currTopicResult.copy(
                        onTopic = onTopicResults + currTopicResult.onTopic,
                        offTopic = offTopicResults + currTopicResult.offTopic,
                        firstOffTopic = min(firstOffTopicResult, currTopicResult.firstOffTopic),
                    )
            }
        }
        var overallOnTopic = 0f
        var overallResults = 0f
        topicResults.keys.sorted().forEach {
            val topicResults = topicResults[it]!!
            println("$it: $topicResults")
            overallOnTopic += topicResults.onTopic
            overallResults += topicResults.onTopic + topicResults.offTopic
        }
        val hitRate = overallOnTopic / overallResults
        println("Hit rate: $hitRate")
    }

    private fun processResourceFilesInPath(
        path: Path,
        block: (String) -> Unit,
    ): Unit =
        Files.list(path).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .forEach { file ->
                    Files.newBufferedReader(file).use { reader ->
                        block(reader.readText())
                    }
                }
        }
}
