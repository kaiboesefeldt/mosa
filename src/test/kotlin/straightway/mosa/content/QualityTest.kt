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
            val overallResults: Int,
            val falsePositives: Int,
            val notFound: Int,
        )

        val topicResuls = mutableMapOf<String, TopicResult>()
        searchable.forEach { s ->
            val overallQueryResult = mutableListOf<QueryResult.Entry>()
            runBlocking {
                val embeddingVector = embeddingService.embed(s.text)
                val searchResult =
                    contentService.query(
                        gid,
                        QueryRequest(
                            embeddingVersion = "1",
                            embeddingVector = embeddingVector.toList(),
                            minSimilarity = 0.89f,
                            maxResults = 40,
                        ),
                    )
                overallQueryResult.addAll(searchResult.entries)
                var searchOnId = searchResult.queryOnId
                while (searchOnId.isNotEmpty()) {
                    val searchResult =
                        contentService.queryOn(
                            gid,
                            QueryOnRequest(
                                searchOnId,
                                maxResults = 40,
                            ),
                        )
                    overallQueryResult.addAll(searchResult.entries)
                    searchOnId = searchResult.queryOnId
                }
            }

            val currTopicResult = topicResuls[s.topic] ?: TopicResult(0, 0, 0)
            val notFound =
                40 - overallQueryResult.count { published[it.content]?.topic == s.topic }
            val falsePositives =
                overallQueryResult.count { published[it.content]?.topic != s.topic }
            topicResuls[s.topic] =
                currTopicResult.copy(
                    falsePositives = falsePositives + currTopicResult.falsePositives,
                    notFound = notFound + currTopicResult.notFound,
                    overallResults = overallQueryResult.size + currTopicResult.overallResults,
                )
        }
        println(topicResuls)
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
