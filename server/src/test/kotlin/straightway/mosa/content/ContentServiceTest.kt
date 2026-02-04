package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.VectorStore.Query
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContentServiceTest {
    private val embeddingService = mockk<EmbeddingService>()
    private val vectorStore = mockk<VectorStore>()
    private val messageDigest = mockk<MessageDigest>()
    private val hasherFactory = mockk<() -> MessageDigest>()
    private val transaction = mockk<VectorStore.Transaction>(relaxed = true)
    private val maxFetchItems = 100
    private val sut = ContentService(embeddingService, vectorStore, hasherFactory, maxFetchItems)

    private val testUserGID = "test-user-gid"
    private val testEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f)
    private val testQueryOnId = UUID.randomUUID()
    private val testLaterThan = 1234567890L
    private val testEarlierThan = 2234567890L

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
                    sut.query(QueryRequest())
                }
            }

        @Test
        fun `queries without threadRootId filter for missing rootId`() =
            runTest {
                setupEmbedding("topic without thread")
                setupVectorStore()

                sut.query(QueryRequest(topic = "topic without thread"))

                verifyQueryCall(
                    Query(
                        vector = testEmbedding,
                        fromTime = null,
                        fields = mapOf("rootId" to null),
                    ),
                )
            }

        @Test
        fun `queries with topic only`() =
            runTest {
                testQuery(
                    request = QueryRequest(topic = "test topic"),
                    expectedQuery = Query(vector = testEmbedding, fromTime = null, fields = mapOf("rootId" to null)),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `queries with mustBeLaterThan only`() =
            runTest {
                testQuery(
                    request = QueryRequest(fromTime = testLaterThan),
                    expectedQuery =
                        Query(
                            vector = null,
                            fromTime = Instant.ofEpochSecond(testLaterThan),
                            fields = mapOf("rootId" to null),
                        ),
                )
            }

        @Test
        fun `queries with mustBeEarlierThan only`() =
            runTest {
                testQuery(
                    request = QueryRequest(toTime = testEarlierThan),
                    expectedQuery =
                        Query(
                            vector = null,
                            toTime = Instant.ofEpochSecond(testEarlierThan),
                            fields = mapOf("rootId" to null),
                        ),
                )
            }

        @Test
        fun `queries with threadRootId only`() =
            runTest {
                testQuery(
                    request = QueryRequest(threadRootId = "root-123"),
                    expectedQuery = Query(vector = null, fromTime = null, fields = mapOf("rootId" to "root-123")),
                )
            }

        @Test
        fun `queries with topic and mustBeLaterThan`() =
            runTest {
                testQuery(
                    request = QueryRequest(topic = "test topic", fromTime = testLaterThan),
                    expectedQuery =
                        Query(
                            vector = testEmbedding,
                            fromTime = Instant.ofEpochSecond(testLaterThan),
                            fields = mapOf("rootId" to null),
                        ),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `queries with topic and threadRootId`() =
            runTest {
                testQuery(
                    request = QueryRequest(topic = "test topic", threadRootId = "root-123"),
                    expectedQuery = Query(vector = testEmbedding, fromTime = null, fields = mapOf("rootId" to "root-123")),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `queries with mustBeLaterThan and threadRootId`() =
            runTest {
                testQuery(
                    request = QueryRequest(fromTime = testLaterThan, threadRootId = "root-123"),
                    expectedQuery =
                        Query(
                            vector = null,
                            fromTime = Instant.ofEpochSecond(testLaterThan),
                            fields =
                                mapOf("rootId" to "root-123"),
                        ),
                )
            }

        @Test
        fun `queries with all parameters`() =
            runTest {
                testQuery(
                    request =
                        QueryRequest(
                            topic = "test topic",
                            fromTime = testLaterThan,
                            toTime = testEarlierThan,
                            threadRootId = "root-123",
                        ),
                    expectedQuery =
                        Query(
                            vector = testEmbedding,
                            fromTime = Instant.ofEpochSecond(testLaterThan),
                            toTime = Instant.ofEpochSecond(testEarlierThan),
                            fields = mapOf("rootId" to "root-123"),
                        ),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `queries with scoreMustBeAbove`() =
            runTest {
                setupEmbedding("above topic")
                var capturedQuery: VectorStore.Query? = null
                setupVectorStoreWithCapture(capture = { capturedQuery = it })

                sut.query(
                    QueryRequest(
                        topic = "above topic",
                        scoreMustBeAbove = 0.42f,
                    ),
                )

                coVerify { vectorStore.query(any()) }
                with(capturedQuery!!) {
                    assertEquals(true, vector?.contentEquals(testEmbedding))
                    assertEquals(0.42f, scoreMustBeAbove)
                    assertNull(scoreMustBeBelow)
                    assertNull(fromTime)
                    assertEquals(mapOf("rootId" to null), fields)
                }
            }

        @Test
        fun `queries with scoreMustBeBelow`() =
            runTest {
                setupEmbedding("below topic")
                var capturedQuery: VectorStore.Query? = null
                setupVectorStoreWithCapture(capture = { capturedQuery = it })

                sut.query(
                    QueryRequest(
                        topic = "below topic",
                        scoreMustBeBelow = 0.13f,
                    ),
                )

                coVerify { vectorStore.query(any()) }
                with(capturedQuery!!) {
                    assertEquals(true, vector?.contentEquals(testEmbedding))
                    assertEquals(null, scoreMustBeAbove)
                    assertEquals(0.13f, scoreMustBeBelow)
                    assertEquals(null, fromTime)
                    assertEquals(mapOf("rootId" to null), fields)
                }
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
            val result = sut.query(request)

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
                val result = sut.query(QueryRequest(topic = "test topic"))

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
                val result = sut.query(QueryRequest(topic = "test topic"))

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
                        sut.query(QueryRequest(topic = "missing author topic"))
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
                        sut.query(QueryRequest(topic = "missing received at topic"))
                    }
                assertEquals("missing-received-at-id", exception.entryId)
                assertEquals("receivedAt", exception.fieldName)
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
            val result = sut.queryOn(QueryOnRequest(queryId))

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
                val result = sut.queryOn(QueryOnRequest(uuid.toString()))

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
                val result = sut.queryOn(QueryOnRequest(uuid.toString()))

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

    @Nested
    inner class Search {
        private suspend fun testSearch(
            request: QueryRequest,
            expectedQuery: VectorStore.Query,
            needsEmbedding: Boolean = false,
        ) {
            // Given
            if (needsEmbedding) setupEmbedding("test topic")
            setupVectorStore()

            // When
            val result = sut.search(request)

            // Then
            verifyQueryCall(expectedQuery)
            val expectedResult =
                SearchResult(
                    entries =
                        listOf(
                            SearchResult.Entry(
                                id = "test-id",
                                similarity = 0.9f,
                            ),
                        ),
                    queryOnId = testQueryOnId.toString(),
                )
            assertEquals(expectedResult, result)
        }

        @Test
        fun `throws BadRequestException when all parameters are null`() =
            runTest {
                assertThrows<BadRequestException> {
                    sut.search(QueryRequest())
                }
            }

        @Test
        fun `searches without threadRootId filter for missing rootId`() =
            runTest {
                setupEmbedding("topic without thread")
                setupVectorStore()

                sut.search(QueryRequest(topic = "topic without thread"))

                verifyQueryCall(
                    Query(
                        vector = testEmbedding,
                        fromTime = null,
                        fields = mapOf("rootId" to null),
                    ),
                )
            }

        @Test
        fun `searches with topic only`() =
            runTest {
                testSearch(
                    request = QueryRequest(topic = "test topic"),
                    expectedQuery = Query(vector = testEmbedding, fromTime = null, fields = mapOf("rootId" to null)),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `searches with mustBeLaterThan only`() =
            runTest {
                testSearch(
                    request = QueryRequest(fromTime = testLaterThan),
                    expectedQuery =
                        Query(
                            vector = null,
                            fromTime = Instant.ofEpochSecond(testLaterThan),
                            fields = mapOf("rootId" to null),
                        ),
                )
            }

        @Test
        fun `searches with threadRootId only`() =
            runTest {
                testSearch(
                    request = QueryRequest(threadRootId = "root-123"),
                    expectedQuery = Query(vector = null, fromTime = null, fields = mapOf("rootId" to "root-123")),
                )
            }

        @Test
        fun `searches with toTime only`() =
            runTest {
                testSearch(
                    request = QueryRequest(toTime = testEarlierThan),
                    expectedQuery =
                        Query(
                            vector = null,
                            toTime = Instant.ofEpochSecond(testEarlierThan),
                            fields = mapOf("rootId" to null),
                        ),
                )
            }

        @Test
        fun `searches with topic and fromTime`() =
            runTest {
                testSearch(
                    request = QueryRequest(topic = "test topic", fromTime = testLaterThan),
                    expectedQuery =
                        Query(
                            vector = testEmbedding,
                            fromTime = Instant.ofEpochSecond(testLaterThan),
                            fields = mapOf("rootId" to null),
                        ),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `searches with topic and threadRootId`() =
            runTest {
                testSearch(
                    request = QueryRequest(topic = "test topic", threadRootId = "root-123"),
                    expectedQuery = Query(vector = testEmbedding, fromTime = null, fields = mapOf("rootId" to "root-123")),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `searches with fromTime and threadRootId`() =
            runTest {
                testSearch(
                    request = QueryRequest(fromTime = testLaterThan, threadRootId = "root-123"),
                    expectedQuery =
                        Query(
                            vector = null,
                            fromTime = Instant.ofEpochSecond(testLaterThan),
                            fields =
                                mapOf("rootId" to "root-123"),
                        ),
                )
            }

        @Test
        fun `searches with all parameters`() =
            runTest {
                testSearch(
                    request =
                        QueryRequest(
                            topic = "test topic",
                            fromTime = testLaterThan,
                            toTime = testEarlierThan,
                            threadRootId = "root-123",
                        ),
                    expectedQuery =
                        Query(
                            vector = testEmbedding,
                            fromTime = Instant.ofEpochSecond(testLaterThan),
                            toTime = Instant.ofEpochSecond(testEarlierThan),
                            fields = mapOf("rootId" to "root-123"),
                        ),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `searches with scoreMustBeAbove`() =
            runTest {
                setupEmbedding("above topic")
                var capturedQuery: VectorStore.Query? = null
                setupVectorStoreWithCapture(capture = { capturedQuery = it })

                sut.search(
                    QueryRequest(
                        topic = "above topic",
                        scoreMustBeAbove = 0.42f,
                    ),
                )

                coVerify { vectorStore.query(any()) }
                with(capturedQuery!!) {
                    assertEquals(true, vector?.contentEquals(testEmbedding))
                    assertEquals(0.42f, scoreMustBeAbove)
                    assertNull(scoreMustBeBelow)
                    assertNull(fromTime)
                    assertEquals(mapOf("rootId" to null), fields)
                }
            }

        @Test
        fun `searches with scoreMustBeBelow`() =
            runTest {
                setupEmbedding("below topic")
                var capturedQuery: VectorStore.Query? = null
                setupVectorStoreWithCapture(capture = { capturedQuery = it })

                sut.search(
                    QueryRequest(
                        topic = "below topic",
                        scoreMustBeBelow = 0.13f,
                    ),
                )

                coVerify { vectorStore.query(any()) }
                with(capturedQuery!!) {
                    assertEquals(true, vector?.contentEquals(testEmbedding))
                    assertEquals(null, scoreMustBeAbove)
                    assertEquals(0.13f, scoreMustBeBelow)
                    assertEquals(null, fromTime)
                    assertEquals(mapOf("rootId" to null), fields)
                }
            }

        @Test
        fun `returns empty queryOnId when VectorStore returns null queryOnId`() =
            runTest {
                // Given
                setupEmbedding("test topic")
                setupVectorStore(createVectorStoreResults(queryOnId = null))

                // When
                val result = sut.search(QueryRequest(topic = "test topic"))

                // Then
                val expectedResult =
                    SearchResult(
                        entries =
                            listOf(
                                SearchResult.Entry(
                                    id = "test-id",
                                    similarity = 0.9f,
                                ),
                            ),
                        queryOnId = "",
                    )
                assertEquals(expectedResult, result)
            }

        @Test
        fun `maps VectorStore entries to SearchResult entries`() =
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
                val result = sut.search(QueryRequest(topic = "test topic"))

                // Then
                val expectedResult =
                    SearchResult(
                        entries =
                            listOf(
                                SearchResult.Entry(
                                    id = "entry-1",
                                    similarity = 0.95f,
                                ),
                                SearchResult.Entry(
                                    id = "entry-2",
                                    similarity = 0.80f,
                                ),
                            ),
                        queryOnId = testQueryOnId.toString(),
                    )
                assertEquals(expectedResult, result)
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
                        sut.search(QueryRequest(topic = "missing author topic"))
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
                        sut.search(QueryRequest(topic = "missing received at topic"))
                    }
                assertEquals("missing-received-at-id", exception.entryId)
                assertEquals("receivedAt", exception.fieldName)
            }
    }

    @Nested
    inner class SearchOn {
        private fun testSearchOn(
            queryId: String,
            expectedUUID: UUID?,
            expectedQueryOnId: String = testQueryOnId.toString(),
        ) = runTest {
            // Given
            coEvery { vectorStore.queryOn(expectedUUID) } returns createDefaultQueryResults()

            // When
            val result = sut.searchOn(QueryOnRequest(queryId))

            // Then
            coVerify { vectorStore.queryOn(expectedUUID) }
            val expectedResult =
                if (expectedQueryOnId.isNotEmpty()) {
                    SearchResult(
                        entries =
                            listOf(
                                SearchResult.Entry(
                                    id = "test-id",
                                    similarity = 0.9f,
                                ),
                            ),
                        queryOnId = expectedQueryOnId,
                    )
                } else {
                    SearchResult(entries = emptyList(), queryOnId = "")
                }
            assertEquals(expectedResult, result)
        }

        @Test
        fun `searched on with valid UUID`() =
            testSearchOn("550e8400-e29b-41d4-a716-446655440000", UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))

        @Test
        fun `searched on with invalid UUID string`() = testSearchOn("invalid-uuid", null)

        @Test
        fun `searched on with empty string`() = testSearchOn("", null)

        @Test
        fun `returns empty queryOnId when VectorStore returns null`() =
            runTest {
                // Given
                val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                coEvery { vectorStore.queryOn(uuid) } returns createVectorStoreResults(queryOnId = null)

                // When
                val result = sut.searchOn(QueryOnRequest(uuid.toString()))

                // Then
                val expectedResult =
                    SearchResult(
                        entries =
                            listOf(
                                SearchResult.Entry(
                                    id = "test-id",
                                    similarity = 0.9f,
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
                val result = sut.searchOn(QueryOnRequest(uuid.toString()))

                // Then
                val expectedResult =
                    SearchResult(
                        entries =
                            listOf(
                                SearchResult.Entry(
                                    id = "query-on-entry",
                                    similarity = 0.85f,
                                ),
                            ),
                        queryOnId = testQueryOnId.toString(),
                    )
                assertEquals(expectedResult, result)
            }
    }

    @Nested
    inner class Fetch {
        @Test
        fun `fetching too many IDs rejects the request`() =
            runTest {
                val service = ContentService(embeddingService, vectorStore, hasherFactory, maxFetchItems = 2)

                val ex =
                    assertFailsWith<BadRequestException> {
                        service.fetch(FetchRequest(listOf("id1", "id2", "id3")))
                    }

                assertEquals("too many itemIds", ex.message)
            }

        @Test
        fun `fetching multiple of IDs returns according content`() =
            runTest {
                val fetchResult =
                    listOf(
                        VectorStore.PostDump(
                            "id1",
                            "content1",
                            mapOf(
                                AUTHOR_GID_KEY to "author1",
                                RECEIVED_AT_KEY to "12345",
                            ),
                        ),
                        VectorStore.PostDump(
                            "id2",
                            "content2",
                            mapOf(
                                AUTHOR_GID_KEY to "author2",
                                RECEIVED_AT_KEY to "23456",
                                ROOT_ID_KEY to "root2",
                            ),
                        ),
                    )
                val idsToFetch = fetchResult.map { it.id }
                coEvery { vectorStore.fetch(idsToFetch) } returns fetchResult

                val result = sut.fetch(FetchRequest(idsToFetch))

                val expected =
                    FetchResult(
                        entries =
                            fetchResult.map {
                                FetchResult.Entry(
                                    it.id,
                                    it.text,
                                    authorGID = it.fields[AUTHOR_GID_KEY]!!,
                                    storedAt = it.fields[RECEIVED_AT_KEY]!!.toLong(),
                                    rootId = it.fields[ROOT_ID_KEY],
                                )
                            },
                    )
                assertEquals(expected, result)
            }

        @Test
        fun `missing mandatory fields raises exception`() =
            runTest {
                listOf(AUTHOR_GID_KEY, RECEIVED_AT_KEY).forEach { missingField ->
                    val fetchResult =
                        listOf(
                            VectorStore.PostDump(
                                "id1",
                                "content1",
                                mapOf(
                                    AUTHOR_GID_KEY to "author1",
                                    RECEIVED_AT_KEY to "12345",
                                ) - missingField,
                            ),
                        )
                    val idsToFetch = fetchResult.map { it.id }
                    coEvery { vectorStore.fetch(idsToFetch) } returns fetchResult

                    val ex =
                        assertFailsWith<MissingVectorMetadataException> {
                            sut.fetch(FetchRequest(idsToFetch))
                        }

                    assertEquals("id1", ex.entryId)
                    assertEquals(missingField, ex.fieldName)
                }
            }
    }

    @Nested
    inner class Delete {
        private val postId = "post-123"

        @Test
        fun `returns not found when item does not exist`() =
            runTest {
                coEvery { vectorStore.fetch(listOf(postId)) } returns emptyList()

                assertThrows<ContentService.ContentNotFoundException> {
                    sut.delete(testUserGID, postId)
                }
                coVerify(exactly = 0) { vectorStore.writeAccess(any()) }
            }

        @Test
        fun `returns forbidden when item belongs to another user`() =
            runTest {
                coEvery { vectorStore.fetch(listOf(postId)) } returns
                    listOf(
                        VectorStore.PostDump(
                            postId,
                            "content",
                            mapOf(
                                AUTHOR_GID_KEY to "other-user",
                            ),
                        ),
                    )

                assertThrows<ContentService.ContentForbiddenException> {
                    sut.delete(testUserGID, postId)
                }
                coVerify(exactly = 0) { vectorStore.writeAccess(any()) }
            }

        @Test
        fun `removes item when user is author`() =
            runTest {
                coEvery { vectorStore.fetch(listOf(postId)) } returns
                    listOf(
                        VectorStore.PostDump(
                            postId,
                            "content",
                            mapOf(
                                AUTHOR_GID_KEY to testUserGID,
                            ),
                        ),
                    )
                setupWriteAccess()

                sut.delete(testUserGID, postId)
                coVerify { transaction.remove(postId) }
            }

        @Test
        fun `throws BadRequestException when id is blank`() =
            runTest {
                val exception =
                    assertThrows<BadRequestException> {
                        sut.delete(testUserGID, "   ")
                    }

                assertEquals("id must not be blank", exception.message)
            }
    }

    @Nested
    inner class DeleteAll {
        @Test
        fun `does nothing when no entries exist`() =
            runTest {
                coEvery {
                    vectorStore.query(
                        Query(fields = mapOf(AUTHOR_GID_KEY to testUserGID)),
                    )
                } returns VectorStore.QueryResults(resultsPage = emptyList(), queryOnId = null)

                val deletedIds = sut.deleteAllForUser(testUserGID)

                coVerify(exactly = 0) { vectorStore.writeAccess(any()) }
                assertEquals(emptyList(), deletedIds)
            }

        @Test
        fun `removes all entries across repeated queries`() =
            runTest {
                val firstPage =
                    VectorStore.QueryResults(
                        resultsPage =
                            listOf(
                                VectorStore.QueryResult(
                                    id = "post-1",
                                    text = "text-1",
                                    similarity = 1.0f,
                                    fields = mapOf(AUTHOR_GID_KEY to testUserGID),
                                ),
                                VectorStore.QueryResult(
                                    id = "post-2",
                                    text = "text-2",
                                    similarity = 1.0f,
                                    fields = mapOf(AUTHOR_GID_KEY to testUserGID),
                                ),
                            ),
                        queryOnId = null,
                    )
                val secondPage =
                    VectorStore.QueryResults(
                        resultsPage =
                            listOf(
                                VectorStore.QueryResult(
                                    id = "reply-1",
                                    text = "text-3",
                                    similarity = 1.0f,
                                    fields = mapOf(AUTHOR_GID_KEY to testUserGID),
                                ),
                            ),
                        queryOnId = null,
                    )
                val emptyPage =
                    VectorStore.QueryResults(
                        resultsPage = emptyList(),
                        queryOnId = null,
                    )
                coEvery {
                    vectorStore.query(
                        Query(fields = mapOf(AUTHOR_GID_KEY to testUserGID)),
                    )
                } returnsMany listOf(firstPage, secondPage, emptyPage)
                setupWriteAccess()

                val deletedIds = sut.deleteAllForUser(testUserGID)

                coVerify { transaction.remove("post-1") }
                coVerify { transaction.remove("post-2") }
                coVerify { transaction.remove("reply-1") }
                assertEquals(listOf("post-1", "post-2", "reply-1"), deletedIds)
            }
    }

    @Nested
    inner class Publish {
        @Test
        fun `throws BadRequest when text exceeds max length`() =
            runTest {
                val tooLongText = "a".repeat(1801)

                assertThrows<BadRequestException> {
                    sut.publish(testUserGID, PublishRequest(tooLongText), TEST_TIME)
                }

                coVerify(exactly = 0) { embeddingService.embed(any()) }
            }

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
        @Test
        fun `throws BadRequest when reply text exceeds max length`() =
            runTest {
                val tooLongText = "a".repeat(1801)

                assertThrows<BadRequestException> {
                    sut.reply(testUserGID, ReplyRequest(PublishRequest(tooLongText), "root-123"), TEST_TIME)
                }

                coVerify(exactly = 0) { embeddingService.embed(any()) }
            }

        @Test
        fun `replies with rootId field set for top level root`() =
            runTest {
                // Given
                val content = "Reply text"
                val testHash = byteArrayOf(5, 6, 7, 8)
                setupEmbedding(content)
                setupHasher(testHash)
                setupWriteAccess()
                coEvery { vectorStore.query(Query(id = "root-123")) } returns
                    createParentQueryResult("root-123", null)

                // When
                val contentId =
                    sut.reply(testUserGID, ReplyRequest(PublishRequest(content), "root-123"), TEST_TIME)

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
                                "rootId" to "root-123",
                            ),
                        vector = testEmbedding,
                    )
                }
            }

        @Test
        fun `throws BadRequest when root is not found`() =
            runTest {
                val unknownRootId = "unknown-root"
                coEvery { vectorStore.query(Query(id = unknownRootId)) } returns
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
                coEvery { vectorStore.query(Query(id = "child-123")) } returns
                    createParentQueryResult("child-123", "root-123")

                // Then
                assertThrows<BadRequestException> { sut.reply(testUserGID, ReplyRequest(PublishRequest(content), "child-123"), TEST_TIME) }
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

    private fun setupVectorStoreWithCapture(
        capture: (VectorStore.Query) -> Unit,
        result: VectorStore.QueryResults = createDefaultQueryResults(),
    ) {
        coEvery { vectorStore.query(any()) } coAnswers {
            capture(firstArg())
            result
        }
    }

    private companion object {
        const val TEST_TIME = 170000000L
    }
}
