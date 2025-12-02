package straightway.mosa.commandline

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExportCommandTest {
    private val streamIO = mockk<PostDumpStreamIO>()
    val outputStream = mockk<OutputStream>(relaxUnitFun = true)
    private val writer = mockk<StreamWriter<VectorStore.PostDump>>()
    private val koin = mockk<Koin>()
    private val koinApplication = mockk<KoinApplication>(relaxed = true)
    private val vectorStore = mockk<VectorStore>()
    val post =
        VectorStore.PostDump(
            "id",
            "text",
            mapOf(AUTHOR_GID_KEY to "author", ROOT_ID_KEY to "root"),
        )

    @BeforeEach
    fun setUp() {
        mockkStatic("org.koin.core.context.DefaultContextExtKt")
        every { koinApplication.koin } returns koin
        every { koinApplication.close() } returns Unit
        every { startKoin(any<KoinApplication.() -> Unit>()) } answers {
            koinApplication.apply(arg(0))
        }
        every { koin.get<VectorStore>() } returns vectorStore
        every { streamIO.writerTo(any()) } returns writer
        coEvery { writer.write(any()) } just runs
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("org.koin.core.context.DefaultContextExtKt")
        clearMocks(streamIO, writer, vectorStore, outputStream, koin, koinApplication)
    }

    @Test
    fun `execute doesn't write anything if the vector store is empty`() =
        runTest {
            every { vectorStore.dumpAll() } returns emptyFlow()
            val sut = ExportCommand(streamIO, outputStream, ExitSignal::exit)

            assertFailsWith<ExitSignal> { sut.execute() }

            coVerify(exactly = 0) { writer.write(any()) }
        }

    @Test
    fun `execute writes all posts retrieved from the vector store`() =
        runTest {
            val posts = arrayOf(post, post.copy(id = "id2"), post.copy(id = "id3"))
            every { vectorStore.dumpAll() } returns flowOf(*posts)
            val sut = ExportCommand(streamIO, outputStream, ExitSignal::exit)

            assertFailsWith<ExitSignal> { sut.execute() }

            posts.forEach {
                coVerify(exactly = 1) { writer.write(it) }
            }
        }

    @Test
    fun `execute exits with exit value 0`() =
        runTest {
            every { vectorStore.dumpAll() } returns emptyFlow()
            val sut = ExportCommand(streamIO, outputStream, ExitSignal::exit)

            val exitSignal = assertFailsWith<ExitSignal> { sut.execute() }

            assertEquals(0, exitSignal.code)
        }

    @Test
    fun `execute closes koinApplication`() =
        runTest {
            every { vectorStore.dumpAll() } returns emptyFlow()
            val sut = ExportCommand(streamIO, outputStream, ExitSignal::exit)

            assertFailsWith<ExitSignal> { sut.execute() }

            verify(exactly = 1) { koinApplication.close() }
        }

    @Test
    fun `execute closes outputStream`() =
        runTest {
            every { vectorStore.dumpAll() } returns emptyFlow()
            val sut = ExportCommand(streamIO, outputStream, ExitSignal::exit)

            assertFailsWith<ExitSignal> { sut.execute() }

            verify(exactly = 1) { outputStream.close() }
        }

    private class ExitSignal(
        val code: Int,
    ) : RuntimeException() {
        companion object {
            fun exit(code: Int): Nothing = throw ExitSignal(code)
        }
    }
}
