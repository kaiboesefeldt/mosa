package straightway.mosa.reranking
/*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import straightway.mosa.testdata.TestPublish
import kotlin.test.AfterTest
import kotlin.test.assertTrue
import kotlin.time.measureTime

class OnnxLocalRerankingServiceTest {
    private val published = TestPublish.load()
    private val modelPath =
        HuggingfaceModelDownloader(
            provider = "BAAI",
            modelName = "bge-reranker-base",
        ).download()
    private val sut = OnnxLocalRerankingService(modelPath)

    @AfterTest
    fun globalTearDown() {
        sut.close()
    }

    @Test
    fun testRank() {
        val result =
            runBlocking {
                sut.rank("Hallo Welt", listOf("Hello World")).also { println("rank: $it") }
            }.single()

        assertTrue(0 < result.score, "Unexpected rank: $result")
    }

    @Test
    fun testRankingQuality() {
        val thresshold = 0.4
        measureTime {
            var overallFalseOffTopics = 0
            var overallOnTopicDocs = 0
            var overallFalseOnTopics = 0
            var overallOffTopicDocs = 0
            published.forEach { (topic, items) ->
                println("\n--------\nTopic '$topic': ${items.first().text}")
                var falseOffTopics = 0
                var falseOnTopics = 0

                val queryDoc = items.first().text

                val selfScore =
                    1e5 * (
                        1.0f -
                            runBlocking {
                                sut.rank(queryDoc, listOf(queryDoc))
                            }.single().score
                    )
                println("Self score: $selfScore")

                val onTopicDocs = items.drop(1).map { it.text }
                overallOnTopicDocs += onTopicDocs.size
                val sameTopicRankings =
                    runBlocking {
                        sut.rank(queryDoc, onTopicDocs)
                    }
                falseOffTopics += sameTopicRankings.count { it.score <= thresshold }

                val offTopicDocs =
                    published
                        .filter { it.key != topic }
                        .flatMap { it.value.map { it.text } }
                overallOffTopicDocs += offTopicDocs.size
                val otherTopicRankings =
                    runBlocking {
                        sut.rank(queryDoc, offTopicDocs)
                    }
                falseOnTopics += otherTopicRankings.count { thresshold <= it.score }

                println("false off topic: $falseOffTopics = ${100.0 * falseOffTopics / onTopicDocs.size.toDouble()}%")
                println("false on topic : $falseOnTopics = ${100.0 * falseOnTopics / offTopicDocs.size.toDouble()}%")

                overallFalseOnTopics += falseOnTopics
                overallFalseOffTopics += falseOffTopics
            }

            println("\n\n========\nOverall results:")
            println("false off topic: $overallFalseOffTopics = ${100.0 * overallFalseOffTopics / overallOnTopicDocs.toDouble()}%")
            println("false on topic : $overallFalseOnTopics = ${100.0 * overallFalseOnTopics / overallOffTopicDocs.toDouble()}%")
        }.also {
            print("Took $it")
        }
    }
}
*/
