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
import straightway.mosa.testutils.TestClock
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class ContentServiceTest {
    private val embeddingService = mockk<EmbeddingService>()
    private val vectorStore = mockk<VectorStore>()
    private val messageDigest = mockk<MessageDigest>()
    private val hasherFactory = mockk<() -> MessageDigest>()
    private val transaction = mockk<VectorStore.Transaction>(relaxed = true)
    private val clock = TestClock(Instant.ofEpochSecond(170000000))
    private val sut = ContentService(embeddingService, vectorStore, hasherFactory, clock)

    private val testUserGID = "test-user-gid"
    private val testEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f)
    private val testQueryOnId = UUID.randomUUID()
    private val testMinTime = Instant.ofEpochSecond(1234567890L)

    private fun setupEmbedding(text: String) {
        coEvery { embeddingService.embed(text) } returns testEmbedding
    }

    private fun setupVectorStore(result: VectorStore.QueryResults = createDefaultQueryResults()) {
        coEvery { vectorStore.query(any()) } returns result
    }

    private fun setupWriteAccess() {
        coEvery { vectorStore.writeAccess(any()) } coAnswers {
            firstArg<suspend VectorStore.Transaction.() -> Unit>()(transaction)
        }
    }

    private fun setupHasher(hash: ByteArray) {
        every { hasherFactory() } returns messageDigest
        every { messageDigest.digest(any()) } returns hash
    }

    private fun verifyQueryCall(expectedQuery: VectorStore.Query) {
        coVerify { vectorStore.query(expectedQuery) }
    }

    private fun createDefaultQueryResults() = createVectorStoreResults()

    private fun createParentQueryResult(
        parentId: String,
        rootId: String? = null,
    ) = VectorStore.QueryResults(
        resultsPage =
            listOf(
                VectorStore.QueryResult(
                    id = parentId,
                    text = "any",
                    similarity = 1.0f,
                    fields = rootId?.let { mapOf("rootId" to it) } ?: emptyMap(),
                ),
            ),
        queryOnId = null,
    )

    @Nested
    inner class Query {
        @Test
        fun `throws BadRequestException when all parameters are null`() =
            runTest {
                assertThrows<BadRequestException> {
                    sut.query(testUserGID, QueryRequest())
                }
            }

        @Test
        fun `queries with topic only`() =
            runTest {
                testQuery(
                    request = QueryRequest(topic = "test topic"),
                    expectedQuery = VectorStore.Query(vector = testEmbedding, fromTime = null, fields = emptyMap()),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `queries with minTime only`() =
            runTest {
                testQuery(
                    request = QueryRequest(minTime = testMinTime),
                    expectedQuery = VectorStore.Query(vector = null, fromTime = testMinTime, fields = emptyMap()),
                )
            }

        @Test
        fun `queries with threadRootId only`() =
            runTest {
                testQuery(
                    request = QueryRequest(threadRootId = "root-123"),
                    expectedQuery = VectorStore.Query(vector = null, fromTime = null, fields = mapOf("rootId" to "root-123")),
                )
            }

        @Test
        fun `queries with topic and minTime`() =
            runTest {
                testQuery(
                    request = QueryRequest(topic = "test topic", minTime = testMinTime),
                    expectedQuery = VectorStore.Query(vector = testEmbedding, fromTime = testMinTime, fields = emptyMap()),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `queries with topic and threadRootId`() =
            runTest {
                testQuery(
                    request = QueryRequest(topic = "test topic", threadRootId = "root-123"),
                    expectedQuery = VectorStore.Query(vector = testEmbedding, fromTime = null, fields = mapOf("rootId" to "root-123")),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `queries with minTime and threadRootId`() =
            runTest {
                testQuery(
                    request = QueryRequest(minTime = testMinTime, threadRootId = "root-123"),
                    expectedQuery = VectorStore.Query(vector = null, fromTime = testMinTime, fields = mapOf("rootId" to "root-123")),
                )
            }

        @Test
        fun `queries with all parameters`() =
            runTest {
                testQuery(
                    request = QueryRequest(topic = "test topic", minTime = testMinTime, threadRootId = "root-123"),
                    expectedQuery =
                        VectorStore.Query(
                            vector = testEmbedding,
                            fromTime = testMinTime,
                            fields = mapOf("rootId" to "root-123"),
                        ),
                    needsEmbedding = true,
                )
            }

        private suspend fun testQuery(
            request: QueryRequest,
            expectedQuery: VectorStore.Query,
            needsEmbedding: Boolean = false,
        ) {
            // Given
            if (needsEmbedding) setupEmbedding("test topic")
            setupVectorStore()

            // When
            val result = sut.query(testUserGID, request)

            // Then
            verifyQueryCall(expectedQuery)
            val expectedResult =
                QueryResult(
                    entries =
                        listOf(
                            QueryResult.Entry(
                                id = "test-id",
                                content = "test content",
                                similarity = 0.9f,
                                authorGID = "test-author",
                                storedAt = 1609459200L,
                                parent = null,
                            ),
                        ),
                    queryOnId = testQueryOnId.toString(),
                )
            assertEquals(expectedResult, result)
        }

        @Test
        fun `returns empty queryOnId when VectorStore returns null queryOnId`() =
            runTest {
                // Given
                setupEmbedding("test topic")
                setupVectorStore(createVectorStoreResults(queryOnId = null))

                // When
                val result = sut.query(testUserGID, QueryRequest(topic = "test topic"))

                // Then
                val expectedResult =
                    QueryResult(
                        entries =
                            listOf(
                                QueryResult.Entry(
                                    id = "test-id",
                                    content = "test content",
                                    similarity = 0.9f,
                                    authorGID = "test-author",
                                    storedAt = 1609459200L,
                                    parent = null,
                                ),
                            ),
                        queryOnId = "",
                    )
                assertEquals(expectedResult, result)
            }

        @Test
        fun `maps VectorStore entries to QueryResult entries`() =
            runTest {
                // Given
                val vectorStoreEntries =
                    listOf(
                        VectorStore.QueryResult(
                            id = "entry-1",
                            text = "First entry",
                            similarity = 0.95f,
                            fields = mapOf("authorGID" to "author-1", "receivedAt" to "1609459200", "parentId" to "parent-1"),
                        ),
                        VectorStore.QueryResult(
                            id = "entry-2",
                            text = "Second entry",
                            similarity = 0.80f,
                            fields = mapOf("authorGID" to "author-2", "receivedAt" to "1609459300"),
                        ),
                    )
                setupEmbedding("test topic")
                setupVectorStore(createVectorStoreResults(entries = vectorStoreEntries))

                // When
                val result = sut.query(testUserGID, QueryRequest(topic = "test topic"))

                // Then
                val expectedResult =
                    QueryResult(
                        entries =
                            listOf(
                                QueryResult.Entry(
                                    id = "entry-1",
                                    content = "First entry",
                                    similarity = 0.95f,
                                    authorGID = "author-1",
                                    storedAt = 1609459200L,
                                    parent = "parent-1",
                                ),
                                QueryResult.Entry(
                                    id = "entry-2",
                                    content = "Second entry",
                                    similarity = 0.80f,
                                    authorGID = "author-2",
                                    storedAt = 1609459300L,
                                    parent = null,
                                ),
                            ),
                        queryOnId = testQueryOnId.toString(),
                    )
                assertEquals(expectedResult, result)
            }
    }

    @Nested
    inner class Publish {
        @Test
        fun `publishes content with correct parameters`() =
            runTest {
                // Given
                val content = "Hello world"
                val testHash = byteArrayOf(1, 2, 3, 4)
                setupEmbedding(content)
                setupHasher(testHash)
                setupWriteAccess()

                // When
                sut.publish(testUserGID, PublishRequest(content))

                // Then
                coVerify { embeddingService.embed(content) }
                coVerify { hasherFactory() }
                coVerify { messageDigest.digest(content.toByteArray(Charsets.UTF_8)) }
                coVerify {
                    transaction.add(
                        id = "AQIDBA", // Base64 of testHash
                        text = content,
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
        private fun testReply(
            parentId: String,
            expectedRootId: String,
            parentRootId: String? = null,
        ) = runTest {
            // Given
            val content = "Reply text"
            val testHash = byteArrayOf(5, 6, 7, 8)
            setupEmbedding(content)
            setupHasher(testHash)
            setupWriteAccess()
            coEvery { vectorStore.query(VectorStore.Query(id = parentId)) } returns
                createParentQueryResult(parentId, parentRootId)

            // When
            sut.reply(testUserGID, ReplyRequest(PublishRequest(content), parentId))

            // Then
            coVerify { embeddingService.embed(content) }
            coVerify {
                transaction.add(
                    id = "BQYHCA", // Base64 of testHash
                    text = content,
                    fields =
                        mapOf(
                            "authorGID" to testUserGID,
                            "receivedAt" to "170000000",
                            "parentId" to parentId,
                            "rootId" to expectedRootId,
                        ),
                    vector = testEmbedding,
                )
            }
        }

        @Test
        fun `replies with parentId and rootId field set for top level parent`() = testReply("parent-123", "parent-123")

        @Test
        fun `replies with parentId and rootId field set for parent being itself a reply`() = testReply("parent-123", "root-123", "root-123")
    }

    @Nested
    inner class QueryOn {
        private fun testQueryOn(
            queryId: String,
            expectedUUID: UUID?,
            expectedQueryOnId: String = testQueryOnId.toString(),
        ) = runTest {
            // Given
            coEvery { vectorStore.queryOn(expectedUUID) } returns createDefaultQueryResults()

            // When
            val result = sut.queryOn(testUserGID, QueryOnRequest(queryId))

            // Then
            coVerify { vectorStore.queryOn(expectedUUID) }
            val expectedResult =
                if (expectedQueryOnId.isNotEmpty()) {
                    QueryResult(
                        entries =
                            listOf(
                                QueryResult.Entry(
                                    id = "test-id",
                                    content = "test content",
                                    similarity = 0.9f,
                                    authorGID = "test-author",
                                    storedAt = 1609459200L,
                                    parent = null,
                                ),
                            ),
                        queryOnId = expectedQueryOnId,
                    )
                } else {
                    QueryResult(entries = emptyList(), queryOnId = "")
                }
            assertEquals(expectedResult, result)
        }

        @Test
        fun `queries on with valid UUID`() =
            testQueryOn("550e8400-e29b-41d4-a716-446655440000", UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))

        @Test
        fun `queries on with invalid UUID string`() = testQueryOn("invalid-uuid", null)

        @Test
        fun `queries on with empty string`() = testQueryOn("", null)

        @Test
        fun `returns empty queryOnId when VectorStore returns null`() =
            runTest {
                // Given
                val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                coEvery { vectorStore.queryOn(uuid) } returns createVectorStoreResults(queryOnId = null)

                // When
                val result = sut.queryOn(testUserGID, QueryOnRequest(uuid.toString()))

                // Then
                val expectedResult =
                    QueryResult(
                        entries =
                            listOf(
                                QueryResult.Entry(
                                    id = "test-id",
                                    content = "test content",
                                    similarity = 0.9f,
                                    authorGID = "test-author",
                                    storedAt = 1609459200L,
                                    parent = null,
                                ),
                            ),
                        queryOnId = "",
                    )
                assertEquals(expectedResult, result)
            }

        @Test
        fun `maps VectorStore entries to QueryResult entries correctly`() =
            runTest {
                // Given
                val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                val vectorStoreEntry =
                    VectorStore.QueryResult(
                        id = "query-on-entry",
                        text = "QueryOn content",
                        similarity = 0.85f,
                        fields = mapOf("authorGID" to "query-on-author", "receivedAt" to "1609459400", "parentId" to "query-on-parent"),
                    )
                coEvery { vectorStore.queryOn(uuid) } returns createVectorStoreResults(entries = listOf(vectorStoreEntry))

                // When
                val result = sut.queryOn(testUserGID, QueryOnRequest(uuid.toString()))

                // Then
                val expectedResult =
                    QueryResult(
                        entries =
                            listOf(
                                QueryResult.Entry(
                                    id = "query-on-entry",
                                    content = "QueryOn content",
                                    similarity = 0.85f,
                                    authorGID = "query-on-author",
                                    storedAt = 1609459400L,
                                    parent = "query-on-parent",
                                ),
                            ),
                        queryOnId = testQueryOnId.toString(),
                    )
                assertEquals(expectedResult, result)
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
