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
    private val transaction = mockk<VectorStore.Transaction>(relaxed = true)
    private val sut = ContentService(embeddingService, vectorStore, hasherFactory)

    private val testUserGID = "test-user-gid"
    private val testEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f)
    private val testQueryOnId = UUID.randomUUID()
    private val testMinTime = 1234567890L

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
        fun `queries without threadRootId uses empty fields map`() =
            runTest {
                setupEmbedding("topic without thread")
                setupVectorStore()

                sut.query(testUserGID, QueryRequest(topic = "topic without thread", threadRootId = null))

                verifyQueryCall(
                    VectorStore.Query(
                        vector = testEmbedding,
                        fromTime = null,
                        fields = emptyMap(),
                    ),
                )
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
                    expectedQuery = VectorStore.Query(vector = null, fromTime = Instant.ofEpochSecond(testMinTime), fields = emptyMap()),
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
                    expectedQuery =
                        VectorStore.Query(
                            vector = testEmbedding,
                            fromTime = Instant.ofEpochSecond(testMinTime),
                            fields = emptyMap(),
                        ),
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
                    expectedQuery =
                        VectorStore.Query(
                            vector = null,
                            fromTime = Instant.ofEpochSecond(testMinTime),
                            fields =
                                mapOf("rootId" to "root-123"),
                        ),
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
                            fromTime = Instant.ofEpochSecond(testMinTime),
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
                                rootId = null,
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
                                    rootId = null,
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
                            fields = mapOf("authorGID" to "author-1", "receivedAt" to "1609459200", "rootId" to "root-1"),
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
                                    rootId = "root-1",
                                ),
                                QueryResult.Entry(
                                    id = "entry-2",
                                    content = "Second entry",
                                    similarity = 0.80f,
                                    authorGID = "author-2",
                                    storedAt = 1609459300L,
                                    rootId = null,
                                ),
                            ),
                        queryOnId = testQueryOnId.toString(),
                    )
                assertEquals(expectedResult, result)
            }

        @Test
        fun `filters entries below score threshold`() =
            runTest {
                setupEmbedding("filtered topic")
                val vectorStoreEntries =
                    listOf(
                        VectorStore.QueryResult(
                            id = "above",
                            text = "Above threshold",
                            similarity = 0.81f,
                            fields = mapOf("authorGID" to "author-a", "receivedAt" to "1609459200"),
                        ),
                        VectorStore.QueryResult(
                            id = "equal",
                            text = "Equal to threshold",
                            similarity = 0.8f,
                            fields = mapOf("authorGID" to "author-b", "receivedAt" to "1609459200"),
                        ),
                    )
                setupVectorStore(createVectorStoreResults(entries = vectorStoreEntries))

                val result =
                    sut.query(
                        testUserGID,
                        QueryRequest(topic = "filtered topic", scoreMustBeAbove = 0.8f),
                    )

                assertEquals(listOf("above"), result.entries.map { it.id })
            }

        @Test
        fun `throws failure when authorGID field is missing`() =
            runTest {
                setupEmbedding("missing author topic")
                val entryMissingAuthor =
                    VectorStore.QueryResult(
                        id = "missing-author-id",
                        text = "entry without author",
                        similarity = 0.5f,
                        fields = mapOf("receivedAt" to "1609459200"),
                    )
                setupVectorStore(createVectorStoreResults(entries = listOf(entryMissingAuthor)))

                val exception =
                    assertThrows<MissingVectorMetadataException> {
                        sut.query(testUserGID, QueryRequest(topic = "missing author topic"))
                    }
                assertEquals("missing-author-id", exception.entryId)
                assertEquals("authorGID", exception.fieldName)
            }

        @Test
        fun `throws failure when receivedAt field is missing`() =
            runTest {
                setupEmbedding("missing received at topic")
                val entryMissingReceivedAt =
                    VectorStore.QueryResult(
                        id = "missing-received-at-id",
                        text = "entry without receivedAt",
                        similarity = 0.6f,
                        fields = mapOf("authorGID" to "author-without-received-at"),
                    )
                setupVectorStore(createVectorStoreResults(entries = listOf(entryMissingReceivedAt)))

                val exception =
                    assertThrows<MissingVectorMetadataException> {
                        sut.query(testUserGID, QueryRequest(topic = "missing received at topic"))
                    }
                assertEquals("missing-received-at-id", exception.entryId)
                assertEquals("receivedAt", exception.fieldName)
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
                val contentId = sut.publish(testUserGID, PublishRequest(content), TEST_TIME)

                // Then
                assertEquals("AQIDBA", contentId)
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
        private fun testReplyToRoot(rootId: String) =
            runTest {
                // Given
                val content = "Reply text"
                val testHash = byteArrayOf(5, 6, 7, 8)
                setupEmbedding(content)
                setupHasher(testHash)
                setupWriteAccess()
                coEvery { vectorStore.query(VectorStore.Query(id = rootId)) } returns
                    createParentQueryResult(rootId, null)

                // When
                val contentId = sut.reply(testUserGID, ReplyRequest(PublishRequest(content), rootId), TEST_TIME)

                // Then
                assertEquals("BQYHCA", contentId)
                coVerify { embeddingService.embed(content) }
                coVerify {
                    transaction.add(
                        id = "BQYHCA", // Base64 of testHash
                        text = content,
                        fields =
                            mapOf(
                                "authorGID" to testUserGID,
                                "receivedAt" to "170000000",
                                "rootId" to rootId,
                            ),
                        vector = testEmbedding,
                    )
                }
            }

        @Test
        fun `replies with rootId field set for top level root`() = testReplyToRoot("root-123")

        @Test
        fun `throws BadRequest when root is not found`() =
            runTest {
                val unknownRootId = "unknown-root"
                coEvery { vectorStore.query(VectorStore.Query(id = unknownRootId)) } returns
                    VectorStore.QueryResults(resultsPage = emptyList(), queryOnId = null)

                assertThrows<BadRequestException> {
                    sut.reply(testUserGID, ReplyRequest(PublishRequest("ignored"), unknownRootId), TEST_TIME)
                }
            }

        @Test
        fun `throws BadRequest when replying to non-root`() =
            runTest {
                // Given: parent is itself a reply (has rootId)
                val content = "Reply text"
                val testHash = byteArrayOf(5, 6, 7, 8)
                setupEmbedding(content)
                setupHasher(testHash)
                coEvery { vectorStore.query(VectorStore.Query(id = "child-123")) } returns
                    createParentQueryResult("child-123", "root-123")

                // Then
                assertThrows<BadRequestException> { sut.reply(testUserGID, ReplyRequest(PublishRequest(content), "child-123"), TEST_TIME) }
            }
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
                                    rootId = null,
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
                                    rootId = null,
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
                        fields = mapOf("authorGID" to "query-on-author", "receivedAt" to "1609459400", "rootId" to "query-on-root"),
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
                                    rootId = "query-on-root",
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

    private companion object {
        const val TEST_TIME = 170000000L
    }
}
