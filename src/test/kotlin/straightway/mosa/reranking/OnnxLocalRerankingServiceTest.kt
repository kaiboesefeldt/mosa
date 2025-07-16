package straightway.mosa.reranking

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.AfterTest
import kotlin.test.assertTrue

class OnnxLocalRerankingServiceTest {
    val modelPath =
        HuggingfaceModelDownloader(
            provider = "BAAI",
            modelName = "bge-reranker-base",
        ).download()
    val sut = OnnxLocalRerankingService(modelPath)

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
}
