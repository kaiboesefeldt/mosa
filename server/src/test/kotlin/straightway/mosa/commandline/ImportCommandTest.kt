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
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImportCommandTest {
    private val streamIO = mockk<PostDumpStreamIO>()
    private val contentService = mockk<ContentService>()
    private val koin = mockk<Koin>()
    private val koinApplication = mockk<KoinApplication>(relaxed = true)
    private val hasherFactory = { MessageDigest.getInstance("SHA-256") }
    private val authorPublicKey = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(32) { it.toByte() })
    val authorBase64 =
        Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(
                MessageDigest
                    .getInstance(
                        "SHA-256",
                    ).digest(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(authorPublicKey)),
            )
    val rootPost =
        VectorStore.PostDump(
            "rootId",
            "root",
            mapOf(
                AUTHOR_GID_KEY to authorBase64,
                AUTHOR_PUBLIC_KEY_KEY to authorPublicKey,
                AUTHOR_SIGNATURE_KEY to "signature",
                SIGNATURE_VERSION_KEY to "7",
                RECEIVED_AT_KEY to "12345",
            ),
        )
    val replyPost =
        VectorStore.PostDump(
            "replyId",
            "reply",
            mapOf(
                AUTHOR_GID_KEY to authorBase64,
                AUTHOR_PUBLIC_KEY_KEY to authorPublicKey,
                AUTHOR_SIGNATURE_KEY to "signature",
                SIGNATURE_VERSION_KEY to "7",
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
        coEvery { contentService.publish(any(), any(), any(), any()) } returns "storedPostId"
        coEvery { contentService.reply(any(), any(), any(), any()) } returns "storedReplyId"
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("org.koin.core.context.DefaultContextExtKt")
        clearMocks(streamIO, contentService, koin, koinApplication)
    }

    @Test
    fun `publishes posts without root id and exits with zero`() {
        setupStreamReader(rootPost)
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val exit = assertFailsWith<ExitSignal> { command.execute() }

        assertEquals(0, exit.code)
        coVerify(exactly = 1) {
            contentService.publish(authorBase64, PublishRequest("root", "signature", 7), 12345L, authorPublicKey)
        }
        coVerify(exactly = 0) { contentService.reply(any(), any(), any(), any()) }
        verify(exactly = 1) { streamIO.readerFrom(any()) }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `fails early when non base64 gids do not match author public key`() {
        setupStreamReader(rootPost.copy(fields = rootPost.fields - AUTHOR_GID_KEY + (AUTHOR_GID_KEY to "otherAuthor")))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val ex = assertFailsWith<RuntimeException> { command.execute() }

        assertEquals("authorGID does not match authorPublicKey", ex.message)
        coVerify(exactly = 0) { contentService.publish(any(), any(), any(), any()) }
    }

    @Test
    fun `accepts empty base64 gids unchanged`() {
        setupStreamReader(rootPost.copy(fields = rootPost.fields - AUTHOR_GID_KEY + (AUTHOR_GID_KEY to "")))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val exit = assertFailsWith<ExitSignal> { command.execute() }

        assertEquals(0, exit.code)
        coVerify(exactly = 1) { contentService.publish("", PublishRequest("root", "signature", 7), 12345L, authorPublicKey) }
    }

    @Test
    fun `keeps unpadded base64 gids unchanged`() {
        setupStreamReader(rootPost.copy(fields = rootPost.fields - AUTHOR_GID_KEY + (AUTHOR_GID_KEY to authorBase64)))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val exit = assertFailsWith<ExitSignal> { command.execute() }

        assertEquals(0, exit.code)
        coVerify(exactly = 1) {
            contentService.publish(authorBase64, PublishRequest("root", "signature", 7), 12345L, authorPublicKey)
        }
    }

    @Test
    fun `uses 0 as timestamp if no received timestamp is given in the dump of the root post`() {
        setupStreamReader(rootPost.copy(fields = rootPost.fields - RECEIVED_AT_KEY))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        assertFailsWith<ExitSignal> { command.execute() }

        coVerify(exactly = 1) { contentService.publish(authorBase64, PublishRequest("root", "signature", 7), 0L, authorPublicKey) }
    }

    @Test
    fun `keeps the original post id when the imported id already matches`() {
        val postDump = rootPost.copy(id = "storedPostId")
        setupStreamReader(postDump)
        coEvery {
            contentService.publish(authorBase64, PublishRequest("root", "signature", 7), 12345L, authorPublicKey)
        } returns "storedPostId"
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val exit = assertFailsWith<ExitSignal> { command.execute() }

        assertEquals(0, exit.code)
        coVerify(exactly = 1) {
            contentService.publish(authorBase64, PublishRequest("root", "signature", 7), 12345L, authorPublicKey)
        }
        coVerify(exactly = 0) { contentService.reply(any(), any(), any(), any()) }
    }

    @Test
    fun `replies for posts with root id`() {
        setupStreamReader(replyPost)
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        assertFailsWith<ExitSignal> { command.execute() }

        coVerify(exactly = 0) { contentService.publish(any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            contentService.reply(
                authorBase64,
                ReplyRequest(PublishRequest("reply", "signature", 7), "rootId"),
                12345L,
                authorPublicKey,
            )
        }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `uses 0 as timestamp if no received timestamp is given in the dump of the reply post`() {
        setupStreamReader(replyPost.copy(fields = replyPost.fields - RECEIVED_AT_KEY))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        assertFailsWith<ExitSignal> { command.execute() }

        coVerify(exactly = 1) {
            contentService.reply(
                authorBase64,
                ReplyRequest(PublishRequest("reply", "signature", 7), "rootId"),
                0L,
                authorPublicKey,
            )
        }
    }

    @Test
    fun `retries posting failed posts with recoverable exceptions`() {
        setupStreamReader(replyPost)
        coEvery {
            contentService.reply(authorBase64, ReplyRequest(PublishRequest("reply", "signature", 7), "rootId"), any(), authorPublicKey)
        } throws ContentService.RootNotFoundException()
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val ex = assertFailsWith<IllegalStateException> { command.execute() }

        assertEquals(
            "Could not import these posts: [PostDump(id=replyId, text=reply, fields={authorGID=$authorBase64, authorPublicKey=$authorPublicKey, authorSignature=signature, signatureVersion=7, rootId=rootId, receivedAt=12345})]",
            ex.message,
        )
        coVerify(exactly = 0) { contentService.publish(any(), any(), any(), any()) }
        coVerify(exactly = 2) {
            contentService.reply(
                authorBase64,
                ReplyRequest(PublishRequest("reply", "signature", 7), "rootId"),
                12345L,
                authorPublicKey,
            )
        }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `aborts import if the error is not recoverable`() {
        setupStreamReader(replyPost)
        coEvery {
            contentService.reply(authorBase64, ReplyRequest(PublishRequest("reply", "signature", 7), "rootId"), any(), authorPublicKey)
        } throws UnsupportedOperationException("Test Error")
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val ex = assertFailsWith<UnsupportedOperationException> { command.execute() }

        assertEquals(
            "Test Error",
            ex.message,
        )
        coVerify(exactly = 0) { contentService.publish(any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            contentService.reply(
                authorBase64,
                ReplyRequest(PublishRequest("reply", "signature", 7), "rootId"),
                12345L,
                authorPublicKey,
            )
        }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `replies for posts with symbolic root id`() {
        setupStreamReader(rootPost, replyPost)
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        assertFailsWith<ExitSignal> { command.execute() }

        coVerify(exactly = 1) {
            contentService.publish(authorBase64, PublishRequest("root", "signature", 7), 12345L, authorPublicKey)
        }
        coVerify(exactly = 1) {
            contentService.reply(
                authorBase64,
                ReplyRequest(PublishRequest("reply", "signature", 7), "storedPostId"),
                12345L,
                authorPublicKey,
            )
        }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `fails early when author gid does not match author public key`() {
        val mismatchingGid = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode("other-gid".encodeToByteArray())
        setupStreamReader(replyPost.copy(fields = replyPost.fields - AUTHOR_GID_KEY + (AUTHOR_GID_KEY to mismatchingGid)))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val ex = assertFailsWith<RuntimeException> { command.execute() }

        assertEquals("authorGID does not match authorPublicKey", ex.message)
        coVerify(exactly = 0) { contentService.publish(any(), any(), any(), any()) }
        coVerify(exactly = 0) { contentService.reply(any(), any(), any(), any()) }
    }

    @Test
    fun `fails early when author public key is not valid base64url`() {
        setupStreamReader(replyPost.copy(fields = replyPost.fields - AUTHOR_PUBLIC_KEY_KEY + (AUTHOR_PUBLIC_KEY_KEY to "%")))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val ex = assertFailsWith<RuntimeException> { command.execute() }

        assertEquals("authorPublicKey is not valid base64url", ex.message)
        coVerify(exactly = 0) { contentService.publish(any(), any(), any(), any()) }
        coVerify(exactly = 0) { contentService.reply(any(), any(), any(), any()) }
    }

    @Test
    fun `fails when author gid is missing and does not exit`() {
        setupStreamReader(replyPost.copy(fields = emptyMap()))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val ex = assertFailsWith<RuntimeException> { command.execute() }

        assertEquals("user without field value for `$AUTHOR_GID_KEY`", ex.message)
        coVerify(exactly = 0) { contentService.publish(any(), any(), any(), any()) }
        coVerify(exactly = 0) { contentService.reply(any(), any(), any(), any()) }
        verify(exactly = 1) { koinApplication.close() }
    }

    @Test
    fun `fails when author public key is missing and does not exit`() {
        setupStreamReader(replyPost.copy(fields = replyPost.fields - AUTHOR_PUBLIC_KEY_KEY))
        val command = ImportCommand(streamIO, ByteArrayInputStream(ByteArray(0)), ExitSignal::exit, hasherFactory)

        val ex = assertFailsWith<RuntimeException> { command.execute() }

        assertEquals("user without field value for `$AUTHOR_PUBLIC_KEY_KEY`", ex.message)
        coVerify(exactly = 0) { contentService.publish(any(), any(), any(), any()) }
        coVerify(exactly = 0) { contentService.reply(any(), any(), any(), any()) }
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
