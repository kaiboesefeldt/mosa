package straightway.mosa.commandline

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory
import straightway.mosa.VectorStore
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExportCommandTest {
    private val vectorStore = mockk<VectorStore>()
    private val originalOut = System.out
    private var originalRootLevel: Level? = null
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        stopKoin()
        mockOnnxEmbeddingService()
        mockVectorStore()
        originalRootLevel = rootLogger().level
        every { vectorStore.dumpAll() } returns flowOf()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        OnnxLocalEmbeddingService.embeddingServiceFactory = null
        unmockkObject(LuceneLocalVectorStore.Companion)
        System.setOut(originalOut)
        rootLogger().level = originalRootLevel
    }

    @Test
    fun `exports all stored entries`() {
        val firstResult =
            VectorStore.QueryResult("first-id", "first text", 0.9f, mapOf("thread" to "alpha"))
        val secondResult = VectorStore.QueryResult("second-id", "second text", 0.8f, emptyMap())

        every { vectorStore.dumpAll() } returns flowOf(firstResult, secondResult)

        val output = captureOutput { ExportCommand(exit = ::throwingExit).execute() }
        val parsedResults = objectMapper.readValue<List<VectorStore.QueryResult>>(output)

        assertEquals(listOf(firstResult, secondResult), parsedResults)
        io.mockk.verify { vectorStore.dumpAll() }
    }

    @Test
    fun `disables logging while exporting`() {
        rootLogger().level = Level.INFO

        assertFailsWith<ExportCommandExit> { ExportCommand(exit = ::throwingExit).execute() }

        assertEquals(Level.OFF, rootLogger().level)
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

    private fun rootLogger(): Logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

    private fun captureOutput(block: () -> Unit): String {
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        try {
            block()
        } catch (_: ExportCommandExit) {
        }
        return outputStream.toString()
    }

    private fun throwingExit(status: Int): Nothing = throw ExportCommandExit(status)
}

private class ExportCommandExit(
    status: Int,
) : RuntimeException("Exit $status")
