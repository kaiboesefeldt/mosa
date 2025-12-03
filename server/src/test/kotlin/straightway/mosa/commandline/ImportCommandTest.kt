package straightway.mosa.commandline

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import straightway.mosa.VectorStore
import straightway.mosa.content.ContentService
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImportCommandTest {
    private val streamIO = mockk<PostDumpStreamIO>()
    private val contentService = mockk<ContentService>()
    private val koin = mockk<Koin>()
    private val koinApplication = mockk<KoinApplication>(relaxed = true)
    val rootPost =
        VectorStore.PostDump(
            "rootId",
            "root",
            mapOf(
                AUTHOR_GID_KEY to "author",
                RECEIVED_AT_KEY to "12345",
            ),
        )
    val replyPost =
        VectorStore.PostDump(
            "replyId",
            "reply",
            mapOf(
                AUTHOR_GID_KEY to "author",
                ROOT_ID_KEY to "rootId",
                RECEIVED_AT_KEY to "12345",
            ),
        )

    @BeforeEach
    fun setUp() {
        mockkStatic("org.koin.core.context.DefaultContextExtKt")
        every { koinApplication.koin } returns koin
        every { koinApplication.close() } returns Unit
        every { startKoin(any<KoinApplication.() -> Unit>()) } answers {
            koinApplication.apply(arg(0))
        }
        every { koin.get<ContentService>() } returns contentService
        coEvery { contentService.publish(any(), any(), any()) } returns "storedPostId"
        coEvery { contentService.reply(any(), any(), any()) } returns "storedReplyId"
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("org.koin.core.context.DefaultContextExtKt")
        clearMocks(streamIO, contentService, koin, koinApplication)
    }

    @Test
    fun `publishes posts without root id and exits with zero`() {
        setupStreamReader(rootPost)
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit)

        val exit = assertFailsWith<ExitSignal> { command.execute() }

        assertEquals(0, exit.code)
        coVerify(exactly = 1) { contentService.publish("author", PublishRequest("root"), 12345L) }
        coVerify(exactly = 0) { contentService.reply(any(), any(), any()) }
        verify(exactly = 1) { streamIO.readerFrom(any()) }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `uses 0 as timestamp if no received timestamp is given in the dump of the root post`() {
        setupStreamReader(rootPost.copy(fields = rootPost.fields - RECEIVED_AT_KEY))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit)

        assertFailsWith<ExitSignal> { command.execute() }

        coVerify(exactly = 1) { contentService.publish("author", PublishRequest("root"), 0L) }
    }

    @Test
    fun `replies for posts with root id`() {
        setupStreamReader(replyPost)
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit)

        assertFailsWith<ExitSignal> { command.execute() }

        coVerify(exactly = 0) { contentService.publish(any(), any(), any()) }
        coVerify(exactly = 1) {
            contentService.reply("author", ReplyRequest(PublishRequest("reply"), "rootId"), 12345L)
        }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `uses 0 as timestamp if no received timestamp is given in the dump of the reply post`() {
        setupStreamReader(replyPost.copy(fields = replyPost.fields - RECEIVED_AT_KEY))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit)

        assertFailsWith<ExitSignal> { command.execute() }

        coVerify(exactly = 1) {
            contentService.reply("author", ReplyRequest(PublishRequest("reply"), "rootId"), 0L)
        }
    }

    @Test
    fun `retries posting failed posts with recoverable exceptions`() {
        setupStreamReader(replyPost)
        coEvery {
            contentService.reply("author", ReplyRequest(PublishRequest("reply"), "rootId"), any())
        } throws ContentService.RootNotFoundException()
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit)

        val ex = assertFailsWith<IllegalStateException> { command.execute() }

        assertEquals(
            "Could not import these posts: [PostDump(id=replyId, text=reply, fields={authorGID=author, rootId=rootId, receivedAt=12345})]",
            ex.message,
        )
        coVerify(exactly = 0) { contentService.publish(any(), any(), any()) }
        coVerify(exactly = 2) {
            contentService.reply("author", ReplyRequest(PublishRequest("reply"), "rootId"), 12345L)
        }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `aborts import if the error is not recoverable`() {
        setupStreamReader(replyPost)
        coEvery {
            contentService.reply("author", ReplyRequest(PublishRequest("reply"), "rootId"), any())
        } throws UnsupportedOperationException("Test Error")
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit)

        val ex = assertFailsWith<UnsupportedOperationException> { command.execute() }

        assertEquals(
            "Test Error",
            ex.message,
        )
        coVerify(exactly = 0) { contentService.publish(any(), any(), any()) }
        coVerify(exactly = 1) {
            contentService.reply("author", ReplyRequest(PublishRequest("reply"), "rootId"), 12345L)
        }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `replies for posts with symbolic root id`() {
        setupStreamReader(rootPost, replyPost)
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit)

        assertFailsWith<ExitSignal> { command.execute() }

        coVerify(exactly = 1) {
            contentService.publish("author", PublishRequest("root"), 12345L)
        }
        coVerify(exactly = 1) {
            contentService.reply("author", ReplyRequest(PublishRequest("reply"), "storedPostId"), 12345L)
        }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `fails when author gid is missing and does not exit`() {
        setupStreamReader(replyPost.copy(fields = emptyMap()))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit)

        val ex = assertFailsWith<RuntimeException> { command.execute() }

        assertEquals("user without field value for `$AUTHOR_GID_KEY`", ex.message)
        coVerify(exactly = 0) { contentService.publish(any(), any(), any()) }
        coVerify(exactly = 0) { contentService.reply(any(), any(), any()) }
        verify(exactly = 1) { koinApplication.close() }
    }

    private fun setupStreamReader(vararg posts: VectorStore.PostDump) {
        val reader = FakeStreamReader(posts.toList())
        every { streamIO.readerFrom(any()) } returns reader
    }

    private class FakeStreamReader(
        posts: List<VectorStore.PostDump>,
    ) : StreamReader<VectorStore.PostDump> {
        private val iterator = posts.iterator()

        override suspend fun readNext(): VectorStore.PostDump? = if (iterator.hasNext()) iterator.next() else null
    }

    private class ExitSignal(
        val code: Int,
    ) : RuntimeException() {
        companion object {
            fun exit(code: Int): Nothing = throw ExitSignal(code)
        }
    }
}
