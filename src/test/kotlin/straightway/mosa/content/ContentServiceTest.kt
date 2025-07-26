package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class ContentServiceTest {
    private val embeddingService = mockk<EmbeddingService>()
    private val vectorStore = mockk<VectorStore>()
    private val messageDigest = mockk<MessageDigest>()
    private val hasherFactory = mockk<() -> MessageDigest>()
    private val sut = ContentService(embeddingService, vectorStore, hasherFactory)

    private val testUserGID = "test-user-gid"
    private val testEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f)
    private val testQueryOnId = UUID.randomUUID()

    @Nested
    inner class Query {
        @Test
        fun `throws BadRequestException when all parameters are null`() =
            runTest {
                val request = QueryRequest()

                assertThrows<BadRequestException> {
                    sut.query(testUserGID, request)
                }
            }

        @Test
        fun `queries with topic only`() =
            runTest {
                val request = QueryRequest(topic = "test topic")
                coEvery { embeddingService.embed("test topic") } returns testEmbedding
                coEvery { vectorStore.query(any()) } returns createVectorStoreResults()

                val result = sut.query(testUserGID, request)

                coVerify {
                    vectorStore.query(
                        VectorStore.Query(
                            vector = testEmbedding,
                            fromTime = null,
                            fields = emptyMap(),
                        ),
                    )
                }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
                assertEquals(1, result.entries.size)
            }

        @Test
        fun `queries with minTime only`() =
            runTest {
                val minTime = Instant.ofEpochSecond(1234567890L)
                val request = QueryRequest(minTime = minTime)
                coEvery { vectorStore.query(any()) } returns createVectorStoreResults()

                val result = sut.query(testUserGID, request)

                coVerify {
                    vectorStore.query(
                        VectorStore.Query(
                            vector = null,
                            fromTime = minTime,
                            fields = emptyMap(),
                        ),
                    )
                }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
            }

        @Test
        fun `queries with threadRootId only`() =
            runTest {
                val request = QueryRequest(threadRootId = "root-123")
                coEvery { vectorStore.query(any()) } returns createVectorStoreResults()

                val result = sut.query(testUserGID, request)

                coVerify {
                    vectorStore.query(
                        VectorStore.Query(
                            vector = null,
                            fromTime = null,
                            fields = mapOf("rootId" to "root-123"),
                        ),
                    )
                }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
            }

        @Test
        fun `queries with topic and minTime`() =
            runTest {
                val minTime = Instant.ofEpochSecond(1234567890L)
                val request = QueryRequest(topic = "test topic", minTime = minTime)
                coEvery { embeddingService.embed("test topic") } returns testEmbedding
                coEvery { vectorStore.query(any()) } returns createVectorStoreResults()

                val result = sut.query(testUserGID, request)

                coVerify {
                    vectorStore.query(
                        VectorStore.Query(
                            vector = testEmbedding,
                            fromTime = minTime,
                            fields = emptyMap(),
                        ),
                    )
                }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
            }

        @Test
        fun `queries with topic and threadRootId`() =
            runTest {
                val request = QueryRequest(topic = "test topic", threadRootId = "root-123")
                coEvery { embeddingService.embed("test topic") } returns testEmbedding
                coEvery { vectorStore.query(any()) } returns createVectorStoreResults()

                val result = sut.query(testUserGID, request)

                coVerify {
                    vectorStore.query(
                        VectorStore.Query(
                            vector = testEmbedding,
                            fromTime = null,
                            fields = mapOf("rootId" to "root-123"),
                        ),
                    )
                }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
            }

        @Test
        fun `queries with minTime and threadRootId`() =
            runTest {
                val minTime = Instant.ofEpochSecond(1234567890L)
                val request = QueryRequest(minTime = minTime, threadRootId = "root-123")
                coEvery { vectorStore.query(any()) } returns createVectorStoreResults()

                val result = sut.query(testUserGID, request)

                coVerify {
                    vectorStore.query(
                        VectorStore.Query(
                            vector = null,
                            fromTime = minTime,
                            fields = mapOf("rootId" to "root-123"),
                        ),
                    )
                }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
            }

        @Test
        fun `queries with all parameters`() =
            runTest {
                val minTime = Instant.ofEpochSecond(1234567890L)
                val request = QueryRequest(topic = "test topic", minTime = minTime, threadRootId = "root-123")
                coEvery { embeddingService.embed("test topic") } returns testEmbedding
                coEvery { vectorStore.query(any()) } returns createVectorStoreResults()

                val result = sut.query(testUserGID, request)

                coVerify {
                    vectorStore.query(
                        VectorStore.Query(
                            vector = testEmbedding,
                            fromTime = minTime,
                            fields = mapOf("rootId" to "root-123"),
                        ),
                    )
                }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
            }

        @Test
        fun `returns empty queryOnId when VectorStore returns null queryOnId`() =
            runTest {
                val request = QueryRequest(topic = "test topic")
                coEvery { embeddingService.embed("test topic") } returns testEmbedding
                coEvery { vectorStore.query(any()) } returns createVectorStoreResults(queryOnId = null)

                val result = sut.query(testUserGID, request)

                assertEquals("", result.queryOnId)
            }

        @Test
        fun `maps VectorStore entries to QueryResult entries`() =
            runTest {
                val request = QueryRequest(topic = "test topic")
                coEvery { embeddingService.embed("test topic") } returns testEmbedding
                coEvery { vectorStore.query(any()) } returns
                    createVectorStoreResults(
                        entries =
                            listOf(
                                VectorStore.QueryResult(
                                    id = "entry-1",
                                    text = "First entry",
                                    similarity = 0.95f,
                                    fields =
                                        mapOf(
                                            "authorGID" to "author-1",
                                            "receivedAt" to "1609459200",
                                            "parentId" to "parent-1",
                                        ),
                                ),
                                VectorStore.QueryResult(
                                    id = "entry-2",
                                    text = "Second entry",
                                    similarity = 0.80f,
                                    fields =
                                        mapOf(
                                            "authorGID" to "author-2",
                                            "receivedAt" to "1609459300",
                                        ),
                                ),
                            ),
                    )

                val result = sut.query(testUserGID, request)

                assertEquals(2, result.entries.size)

                val firstEntry = result.entries[0]
                assertEquals("entry-1", firstEntry.id)
                assertEquals("First entry", firstEntry.content)
                assertEquals(0.95f, firstEntry.similarity)
                assertEquals("author-1", firstEntry.authorGID)
                assertEquals(1609459200L, firstEntry.storedAt)
                assertEquals("parent-1", firstEntry.parent)

                val secondEntry = result.entries[1]
                assertEquals("entry-2", secondEntry.id)
                assertEquals("Second entry", secondEntry.content)
                assertEquals(0.80f, secondEntry.similarity)
                assertEquals("author-2", secondEntry.authorGID)
                assertEquals(1609459300L, secondEntry.storedAt)
                assertEquals(null, secondEntry.parent)
            }
    }

    @Nested
    inner class Publish {
        @Test
        fun `publishes content with correct parameters`() =
            runTest {
                val request = PublishRequest("Hello world")
                val testHash = byteArrayOf(1, 2, 3, 4)
                val transaction = mockk<VectorStore.Transaction>(relaxed = true)

                every { hasherFactory() } returns messageDigest
                every { messageDigest.digest(any()) } returns testHash
                coEvery { embeddingService.embed("Hello world") } returns testEmbedding
                coEvery { vectorStore.writeAccess(any()) } coAnswers {
                    firstArg<suspend VectorStore.Transaction.() -> Unit>()(transaction)
                }

                sut.publish(testUserGID, request)

                coVerify { embeddingService.embed("Hello world") }
                coVerify { hasherFactory() }
                coVerify { messageDigest.digest("Hello world".toByteArray(Charsets.UTF_8)) }
                coVerify {
                    transaction.add(
                        id = "AQIDBA", // Base64 of testHash
                        text = "Hello world",
                        fields =
                            match { fields ->
                                fields["authorGID"] == testUserGID &&
                                    fields.containsKey("receivedAt") &&
                                    fields["receivedAt"]?.toLongOrNull() != null
                            },
                        vector = testEmbedding,
                    )
                }
            }
    }

    @Nested
    inner class Reply {
        @Test
        fun `replies with parentId field set`() =
            runTest {
                val publishRequest = PublishRequest("Reply text")
                val request = ReplyRequest(publishRequest, "parent-123")
                val testHash = byteArrayOf(5, 6, 7, 8)
                val transaction = mockk<VectorStore.Transaction>(relaxed = true)

                every { hasherFactory() } returns messageDigest
                every { messageDigest.digest(any()) } returns testHash
                coEvery { embeddingService.embed("Reply text") } returns testEmbedding
                coEvery { vectorStore.writeAccess(any()) } coAnswers {
                    firstArg<suspend VectorStore.Transaction.() -> Unit>()(transaction)
                }

                sut.reply(testUserGID, request)

                coVerify { embeddingService.embed("Reply text") }
                coVerify {
                    transaction.add(
                        id = "BQYHCA", // Base64 of testHash
                        text = "Reply text",
                        fields =
                            match { fields ->
                                fields["authorGID"] == testUserGID &&
                                    fields.containsKey("receivedAt") &&
                                    fields["parentId"] == "parent-123"
                            },
                        vector = testEmbedding,
                    )
                }
            }
    }

    @Nested
    inner class QueryOn {
        @Test
        fun `queries on with valid UUID`() =
            runTest {
                val request = QueryOnRequest("550e8400-e29b-41d4-a716-446655440000", 10)
                val expectedUUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                coEvery { vectorStore.queryOn(expectedUUID) } returns createVectorStoreResults()

                val result = sut.queryOn(testUserGID, request)

                coVerify { vectorStore.queryOn(expectedUUID) }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
                assertEquals(1, result.entries.size)
            }

        @Test
        fun `queries on with invalid UUID string`() =
            runTest {
                val request = QueryOnRequest("invalid-uuid", 10)
                coEvery { vectorStore.queryOn(null) } returns createVectorStoreResults()

                val result = sut.queryOn(testUserGID, request)

                coVerify { vectorStore.queryOn(null) }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
            }

        @Test
        fun `queries on with empty string`() =
            runTest {
                val request = QueryOnRequest("", 10)
                coEvery { vectorStore.queryOn(null) } returns createVectorStoreResults()

                val result = sut.queryOn(testUserGID, request)

                coVerify { vectorStore.queryOn(null) }
                assertEquals(testQueryOnId.toString(), result.queryOnId)
            }

        @Test
        fun `returns empty queryOnId when VectorStore returns null`() =
            runTest {
                val request = QueryOnRequest("550e8400-e29b-41d4-a716-446655440000", 10)
                val expectedUUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                coEvery { vectorStore.queryOn(expectedUUID) } returns createVectorStoreResults(queryOnId = null)

                val result = sut.queryOn(testUserGID, request)

                assertEquals("", result.queryOnId)
            }

        @Test
        fun `maps VectorStore entries to QueryResult entries correctly`() =
            runTest {
                val request = QueryOnRequest("550e8400-e29b-41d4-a716-446655440000", 10)
                val expectedUUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                coEvery { vectorStore.queryOn(expectedUUID) } returns
                    createVectorStoreResults(
                        entries =
                            listOf(
                                VectorStore.QueryResult(
                                    id = "query-on-entry",
                                    text = "QueryOn content",
                                    similarity = 0.85f,
                                    fields =
                                        mapOf(
                                            "authorGID" to "query-on-author",
                                            "receivedAt" to "1609459400",
                                            "parentId" to "query-on-parent",
                                        ),
                                ),
                            ),
                    )

                val result = sut.queryOn(testUserGID, request)

                assertEquals(1, result.entries.size)
                val entry = result.entries[0]
                assertEquals("query-on-entry", entry.id)
                assertEquals("QueryOn content", entry.content)
                assertEquals(0.85f, entry.similarity)
                assertEquals("query-on-author", entry.authorGID)
                assertEquals(1609459400L, entry.storedAt)
                assertEquals("query-on-parent", entry.parent)
            }
    }

    private fun createVectorStoreResults(
        queryOnId: UUID? = testQueryOnId,
        entries: List<VectorStore.QueryResult> =
            listOf(
                VectorStore.QueryResult(
                    id = "test-id",
                    text = "test content",
                    similarity = 0.9f,
                    fields =
                        mapOf(
                            "authorGID" to "test-author",
                            "receivedAt" to "1609459200",
                        ),
                ),
            ),
    ) = VectorStore.QueryResults(
        queryOnId = queryOnId,
        resultsPage = entries,
    )
}
