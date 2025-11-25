package straightway.mosa.commandline

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import straightway.mosa.VectorStore
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals

class ExportCommandTest {
    private val vectorStore = mockk<VectorStore>()
    private val originalOut = System.out

    @BeforeEach
    fun setUp() {
        stopKoin()
        mockOnnxEmbeddingService()
        mockVectorStore()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        OnnxLocalEmbeddingService.embeddingServiceFactory = null
        unmockkObject(LuceneLocalVectorStore.Companion)
        System.setOut(originalOut)
    }

    @Test
    fun `exports all stored entries`() {
        val firstResult =
            VectorStore.QueryResult("first-id", "first text", 0.9f, mapOf("thread" to "alpha"))
        val secondResult = VectorStore.QueryResult("second-id", "second text", 0.8f, emptyMap())

        every { vectorStore.dumpAll() } returns flowOf(firstResult, secondResult)

        val output = captureOutput { ExportCommand().execute() }

        assertEquals(listOf(firstResult.toString(), secondResult.toString()), output)
        io.mockk.verify { vectorStore.dumpAll() }
    }

    private fun mockOnnxEmbeddingService() {
        OnnxLocalEmbeddingService.embeddingServiceFactory =
            OnnxLocalEmbeddingService.Factory {
                mockk {
                    every { dimension } returns 3
                    every { close() } returns Unit
                }
            }
    }

    private fun mockVectorStore() {
        mockkObject(LuceneLocalVectorStore.Companion)
        every {
            LuceneLocalVectorStore.create(
                indexAccess = any(),
                paginationRegistry = any(),
                queryPlanner = any(),
                clock = any(),
                vectorSize = any(),
            )
        } returns vectorStore
        every { vectorStore.close() } returns Unit
    }

    private fun captureOutput(block: () -> Unit): List<String> {
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        block()
        return outputStream.toString().lines().filter { it.isNotBlank() }
    }
}
