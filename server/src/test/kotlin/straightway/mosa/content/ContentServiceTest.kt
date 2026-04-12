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
import straightway.mosa.VectorStore.Query
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentServiceTest {
    private val embeddingService = mockk<EmbeddingService>()
    private val vectorStore = mockk<VectorStore>()
    private val messageDigest = mockk<MessageDigest>()
    private val hasherFactory = mockk<() -> MessageDigest>()
    private val transaction = mockk<VectorStore.Transaction>(relaxed = true)
    private val maxFetchItems = 100
    private val sut =
        ContentService(
            embeddingService = embeddingService,
            vectorStore = vectorStore,
            hasherFactory = hasherFactory,
            maxFetchItems = maxFetchItems,
            replyIdFactory = { "reply-id" },
        )

    private val testUserGID = "test-user-gid"
    private val testEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f)
    private val testQueryOnId = UUID.randomUUID()
    private val testLaterThan = 1234567890L
    private val testEarlierThan = 2234567890L
    private val testPublicKey = "test-public-key"
    private val testSignature = "test-signature"

    private fun contentService(
        isAdmin: suspend (String) -> Boolean = { false },
        isBlocked: suspend (String) -> Boolean = { false },
    ) = ContentService(
        embeddingService = embeddingService,
        vectorStore = vectorStore,
        hasherFactory = hasherFactory,
        maxFetchItems = maxFetchItems,
        replyIdFactory = { "reply-id" },
        isAdmin = isAdmin,
        isBlocked = isBlocked,
    )

    private fun setupEmbedding(text: String) {
        coEvery { embeddingService.embed(text) } returns testEmbedding
    }

    private fun setupVectorStore(result: VectorStore.QueryResults = createVectorStoreResults()) {
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
                    fields = rootId?.let { mapOf(ROOT_ID_KEY to it) } ?: emptyMap(),
                ),
            ),
        queryOnId = null,
    )

    @Nested
    inner class ExportCsvForUser {
        @Test
        fun `exports only posts of the requested user in deterministic order`() =
            runTest {
                coEvery {
                    vectorStore.dumpAll()
                } returns
                    kotlinx.coroutines.flow.flowOf(
                        VectorStore.PostDump(
                            id = "reply-2",
                            text = "Reply text",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to testUserGID,
                                    RECEIVED_AT_KEY to "200",
                                    ROOT_ID_KEY to "root-1",
                                ),
                        ),
                        VectorStore.PostDump(
                            id = "foreign",
                            text = "Should not appear",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to "other-user",
                                    RECEIVED_AT_KEY to "150",
                                ),
                        ),
                        VectorStore.PostDump(
                            id = "root-2",
                            text = "Second root",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to testUserGID,
                                    RECEIVED_AT_KEY to "200",
                                ),
                        ),
                        VectorStore.PostDump(
                            id = "root-1",
                            text = "First root",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to testUserGID,
                                    RECEIVED_AT_KEY to "100",
                                ),
                        ),
                    )

                val csv = sut.exportCsvForUser(testUserGID)

                assertEquals(
                    """
                    id,type,root_id,stored_at_epoch,stored_at_iso8601,author_gid,text
                    root-1,post,,100,1970-01-01T00:01:40Z,test-user-gid,"First root"
                    reply-2,reply,root-1,200,1970-01-01T00:03:20Z,test-user-gid,"Reply text"
                    root-2,post,,200,1970-01-01T00:03:20Z,test-user-gid,"Second root"
                    """.trimIndent(),
                    csv,
                )
            }

        @Test
        fun `escapes csv fields according to rfc 4180`() =
            runTest {
                coEvery {
                    vectorStore.dumpAll()
                } returns
                    kotlinx.coroutines.flow.flowOf(
                        VectorStore.PostDump(
                            id = "entry-1",
                            text = "Hello,\n\"MOSA\"",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to testUserGID,
                                    RECEIVED_AT_KEY to "100",
                                ),
                        ),
                    )

                val csv = sut.exportCsvForUser(testUserGID)

                assertTrue(csv.contains("\"Hello,\n\"\"MOSA\"\"\""))
            }

        @Test
        fun `quotes id field when it contains double quotes`() =
            runTest {
                coEvery {
                    vectorStore.dumpAll()
                } returns
                    kotlinx.coroutines.flow.flowOf(
                        VectorStore.PostDump(
                            id = "entry-\"1\"",
                            text = "Hello",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to testUserGID,
                                    RECEIVED_AT_KEY to "100",
                                ),
                        ),
                    )

                val csv = sut.exportCsvForUser(testUserGID)

                assertTrue(csv.contains("\"entry-\"\"1\"\"\""))
            }

        @Test
        fun `quotes author field when it contains a comma`() =
            runTest {
                val authorGid = "user,1"
                coEvery {
                    vectorStore.dumpAll()
                } returns
                    kotlinx.coroutines.flow.flowOf(
                        VectorStore.PostDump(
                            id = "entry-1",
                            text = "Hello",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to authorGid,
                                    RECEIVED_AT_KEY to "100",
                                ),
                        ),
                    )

                val csv = sut.exportCsvForUser(authorGid)

                assertTrue(csv.contains("\"user,1\""))
            }

        @Test
        fun `quotes rootId field when it contains a newline`() =
            runTest {
                coEvery {
                    vectorStore.dumpAll()
                } returns
                    kotlinx.coroutines.flow.flowOf(
                        VectorStore.PostDump(
                            id = "entry-1",
                            text = "Hello",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to testUserGID,
                                    RECEIVED_AT_KEY to "100",
                                    ROOT_ID_KEY to "root\n1",
                                ),
                        ),
                    )

                val csv = sut.exportCsvForUser(testUserGID)

                assertTrue(csv.contains("\"root\n1\""))
            }

        @Test
        fun `quotes id field when it contains a carriage return`() =
            runTest {
                coEvery {
                    vectorStore.dumpAll()
                } returns
                    kotlinx.coroutines.flow.flowOf(
                        VectorStore.PostDump(
                            id = "entry-\r1",
                            text = "Hello",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to testUserGID,
                                    RECEIVED_AT_KEY to "100",
                                ),
                        ),
                    )

                val csv = sut.exportCsvForUser(testUserGID)

                assertTrue(csv.contains("\"entry-\r1\""))
            }

        @Test
        fun `returns only the header when user has no posts`() =
            runTest {
                coEvery {
                    vectorStore.dumpAll()
                } returns
                    kotlinx.coroutines.flow.flowOf(
                        VectorStore.PostDump(
                            id = "foreign",
                            text = "Should not appear",
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to "other-user",
                                    RECEIVED_AT_KEY to "150",
                                ),
                        ),
                    )

                val csv = sut.exportCsvForUser(testUserGID)

                assertEquals("id,type,root_id,stored_at_epoch,stored_at_iso8601,author_gid,text", csv)
            }

        @Test
        fun `throws when export entry is missing author metadata`() =
            runTest {
                coEvery {
                    vectorStore.dumpAll()
                } returns
                    kotlinx.coroutines.flow.flowOf(
                        VectorStore.PostDump(
                            id = "entry-1",
                            text = "Hello",
                            fields = mapOf(RECEIVED_AT_KEY to "100"),
                        ),
                    )

                assertFailsWith<MissingVectorMetadataException> {
                    sut.exportCsvForUser(testUserGID)
                }
            }

        @Test
        fun `throws when export entry is missing timestamp metadata`() =
            runTest {
                coEvery {
                    vectorStore.dumpAll()
                } returns
                    kotlinx.coroutines.flow.flowOf(
                        VectorStore.PostDump(
                            id = "entry-1",
                            text = "Hello",
                            fields = mapOf(AUTHOR_GID_KEY to testUserGID),
                        ),
                    )

                assertFailsWith<MissingVectorMetadataException> {
                    sut.exportCsvForUser(testUserGID)
                }
            }
    }

    @Nested
    inner class ContentCapabilities {
        @Test
        fun `returns admin and blocked status for requested gid`() =
            runTest {
                val result =
                    contentService(
                        isAdmin = { gid -> gid == "gid-target" },
                        isBlocked = { gid -> gid == "gid-target" },
                    ).contentCapabilities(
                        userGID = testUserGID,
                        targetGID = "gid-target",
                    )

                assertEquals(
                    ContentCapabilitiesResult(isAdmin = true, isBlocked = true),
                    result,
                )
            }
    }

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
                        fields = mapOf(ROOT_ID_KEY to null),
                    ),
                )
            }

        @Test
        fun `queries with topic only`() =
            runTest {
                testQuery(
                    request = QueryRequest(topic = "test topic"),
                    expectedQuery = Query(vector = testEmbedding, fromTime = null, fields = mapOf(ROOT_ID_KEY to null)),
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
                            fields = mapOf(ROOT_ID_KEY to null),
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
                            fields = mapOf(ROOT_ID_KEY to null),
                        ),
                )
            }

        @Test
        fun `queries with threadRootId only`() =
            runTest {
                testQuery(
                    request = QueryRequest(threadRootId = "root-123"),
                    expectedQuery = Query(vector = null, fromTime = null, fields = mapOf(ROOT_ID_KEY to "root-123")),
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
                            fields = mapOf(ROOT_ID_KEY to null),
                        ),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `queries with topic and threadRootId`() =
            runTest {
                testQuery(
                    request = QueryRequest(topic = "test topic", threadRootId = "root-123"),
                    expectedQuery = Query(vector = testEmbedding, fromTime = null, fields = mapOf(ROOT_ID_KEY to "root-123")),
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
                                mapOf(ROOT_ID_KEY to "root-123"),
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
                            fields = mapOf(ROOT_ID_KEY to "root-123"),
                        ),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `queries with scoreMustBeAbove`() =
            runTest {
                assertScoreQuery(
                    request = QueryRequest(topic = "above topic", scoreMustBeAbove = 0.42f),
                    expectedScoreAbove = 0.42f,
                    expectedScoreBelow = null,
                    perform = sut::query,
                )
            }

        @Test
        fun `queries with scoreMustBeBelow`() =
            runTest {
                assertScoreQuery(
                    request = QueryRequest(topic = "below topic", scoreMustBeBelow = 0.13f),
                    expectedScoreAbove = null,
                    expectedScoreBelow = 0.13f,
                    perform = sut::query,
                )
            }

        private suspend fun testQuery(
            request: QueryRequest,
            expectedQuery: VectorStore.Query,
            needsEmbedding: Boolean = false,
        ) {
            if (needsEmbedding) setupEmbedding(request.topic ?: "")
            setupVectorStore()

            val result = sut.query(request)

            verifyQueryCall(expectedQuery)
            assertEquals(expectedQueryResult(), result)
        }

        @Test
        fun `returns empty queryOnId when VectorStore returns null queryOnId`() =
            runTest {
                setupEmbedding("test topic")
                setupVectorStore(createVectorStoreResults(queryOnId = null))

                val result = sut.query(QueryRequest(topic = "test topic"))

                assertEquals(expectedQueryResult(queryOnId = ""), result)
            }

        @Test
        fun `maps VectorStore entries to QueryResult entries`() =
            runTest {
                val vectorStoreEntries =
                    listOf(
                        VectorStore.QueryResult(
                            id = "entry-1",
                            text = "First entry",
                            similarity = 0.95f,
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to "author-1",
                                    AUTHOR_PUBLIC_KEY_KEY to "public-key-1",
                                    AUTHOR_SIGNATURE_KEY to "signature-1",
                                    SIGNATURE_VERSION_KEY to "11",
                                    RECEIVED_AT_KEY to "1609459200",
                                    ROOT_ID_KEY to "root-1",
                                ),
                        ),
                        VectorStore.QueryResult(
                            id = "entry-2",
                            text = "Second entry",
                            similarity = 0.80f,
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to "author-2",
                                    AUTHOR_PUBLIC_KEY_KEY to "public-key-2",
                                    AUTHOR_SIGNATURE_KEY to "signature-2",
                                    SIGNATURE_VERSION_KEY to "13",
                                    RECEIVED_AT_KEY to "1609459300",
                                ),
                        ),
                    )
                setupEmbedding("test topic")
                setupVectorStore(createVectorStoreResults(entries = vectorStoreEntries))

                val result = sut.query(QueryRequest(topic = "test topic"))

                assertEquals(
                    expectedQueryResult(
                        entries =
                            listOf(
                                defaultQueryResultEntry(
                                    id = "entry-1",
                                    content = "First entry",
                                    similarity = 0.95f,
                                    authorGID = "author-1",
                                    storedAt = 1609459200L,
                                    rootId = "root-1",
                                    authorPublicKey = "public-key-1",
                                    authorSignature = "signature-1",
                                    signatureVersion = 11,
                                ),
                                defaultQueryResultEntry(
                                    id = "entry-2",
                                    content = "Second entry",
                                    similarity = 0.80f,
                                    authorGID = "author-2",
                                    storedAt = 1609459300L,
                                    authorPublicKey = "public-key-2",
                                    authorSignature = "signature-2",
                                    signatureVersion = 13,
                                ),
                            ),
                    ),
                    result,
                )
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
                        fields = mapOf(RECEIVED_AT_KEY to "1609459200"),
                    )
                setupVectorStore(createVectorStoreResults(entries = listOf(entryMissingAuthor)))

                val exception =
                    assertThrows<MissingVectorMetadataException> {
                        sut.query(QueryRequest(topic = "missing author topic"))
                    }
                assertEquals("missing-author-id", exception.entryId)
                assertEquals(AUTHOR_GID_KEY, exception.fieldName)
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
                        fields =
                            mapOf(
                                AUTHOR_GID_KEY to "author-without-received-at",
                                AUTHOR_PUBLIC_KEY_KEY to "public-key-without-received-at",
                                AUTHOR_SIGNATURE_KEY to "signature-without-received-at",
                                SIGNATURE_VERSION_KEY to "1",
                            ),
                    )
                setupVectorStore(createVectorStoreResults(entries = listOf(entryMissingReceivedAt)))

                val exception =
                    assertThrows<MissingVectorMetadataException> {
                        sut.query(QueryRequest(topic = "missing received at topic"))
                    }
                assertEquals("missing-received-at-id", exception.entryId)
                assertEquals(RECEIVED_AT_KEY, exception.fieldName)
            }

        @Test
        fun `throws failure when authorPublicKey field is missing`() =
            runTest {
                setupEmbedding("missing public key topic")
                val entryMissingAuthorPublicKey =
                    VectorStore.QueryResult(
                        id = "missing-public-key-id",
                        text = "entry without public key",
                        similarity = 0.7f,
                        fields =
                            mapOf(
                                AUTHOR_GID_KEY to "author-1",
                                AUTHOR_SIGNATURE_KEY to "signature-1",
                                SIGNATURE_VERSION_KEY to "1",
                                RECEIVED_AT_KEY to "1609459200",
                            ),
                    )
                setupVectorStore(createVectorStoreResults(entries = listOf(entryMissingAuthorPublicKey)))

                val exception =
                    assertThrows<MissingVectorMetadataException> {
                        sut.query(QueryRequest(topic = "missing public key topic"))
                    }
                assertEquals("missing-public-key-id", exception.entryId)
                assertEquals(AUTHOR_PUBLIC_KEY_KEY, exception.fieldName)
            }
    }

    @Nested
    inner class QueryOn {
        private fun testQueryOn(
            queryId: String,
            expectedUUID: UUID?,
            expectedQueryOnId: String = testQueryOnId.toString(),
        ) = runTest {
            coEvery { vectorStore.queryOn(expectedUUID) } returns createVectorStoreResults()

            val result = sut.queryOn(QueryOnRequest(queryId))

            coVerify { vectorStore.queryOn(expectedUUID) }
            val expectedResult =
                if (expectedQueryOnId.isNotEmpty()) {
                    expectedQueryResult(queryOnId = expectedQueryOnId)
                } else {
                    expectedQueryResult(entries = emptyList(), queryOnId = "")
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
                val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                coEvery { vectorStore.queryOn(uuid) } returns createVectorStoreResults(queryOnId = null)

                val result = sut.queryOn(QueryOnRequest(uuid.toString()))

                assertEquals(expectedQueryResult(queryOnId = ""), result)
            }

        @Test
        fun `maps VectorStore entries to QueryResult entries correctly`() =
            runTest {
                val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                val vectorStoreEntry =
                    VectorStore.QueryResult(
                        id = "query-on-entry",
                        text = "QueryOn content",
                        similarity = 0.85f,
                        fields =
                            mapOf(
                                AUTHOR_GID_KEY to "query-on-author",
                                AUTHOR_PUBLIC_KEY_KEY to "query-on-public-key",
                                AUTHOR_SIGNATURE_KEY to "query-on-signature",
                                SIGNATURE_VERSION_KEY to "17",
                                RECEIVED_AT_KEY to "1609459400",
                                ROOT_ID_KEY to "query-on-root",
                            ),
                    )
                coEvery { vectorStore.queryOn(uuid) } returns createVectorStoreResults(entries = listOf(vectorStoreEntry))

                val result = sut.queryOn(QueryOnRequest(uuid.toString()))

                assertEquals(
                    expectedQueryResult(
                        entries =
                            listOf(
                                defaultQueryResultEntry(
                                    id = "query-on-entry",
                                    content = "QueryOn content",
                                    similarity = 0.85f,
                                    authorGID = "query-on-author",
                                    storedAt = 1609459400L,
                                    rootId = "query-on-root",
                                    authorPublicKey = "query-on-public-key",
                                    authorSignature = "query-on-signature",
                                    signatureVersion = 17,
                                ),
                            ),
                    ),
                    result,
                )
            }
    }

    @Nested
    inner class Search {
        private suspend fun testSearch(
            request: QueryRequest,
            expectedQuery: VectorStore.Query,
            needsEmbedding: Boolean = false,
        ) {
            if (needsEmbedding) setupEmbedding(request.topic ?: "")
            setupVectorStore()

            val result = sut.search(request)

            verifyQueryCall(expectedQuery)
            assertEquals(expectedSearchResult(), result)
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
                        fields = mapOf(ROOT_ID_KEY to null),
                    ),
                )
            }

        @Test
        fun `searches with topic only`() =
            runTest {
                testSearch(
                    request = QueryRequest(topic = "test topic"),
                    expectedQuery = Query(vector = testEmbedding, fromTime = null, fields = mapOf(ROOT_ID_KEY to null)),
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
                            fields = mapOf(ROOT_ID_KEY to null),
                        ),
                )
            }

        @Test
        fun `searches with threadRootId only`() =
            runTest {
                testSearch(
                    request = QueryRequest(threadRootId = "root-123"),
                    expectedQuery = Query(vector = null, fromTime = null, fields = mapOf(ROOT_ID_KEY to "root-123")),
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
                            fields = mapOf(ROOT_ID_KEY to null),
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
                            fields = mapOf(ROOT_ID_KEY to null),
                        ),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `searches with topic and threadRootId`() =
            runTest {
                testSearch(
                    request = QueryRequest(topic = "test topic", threadRootId = "root-123"),
                    expectedQuery = Query(vector = testEmbedding, fromTime = null, fields = mapOf(ROOT_ID_KEY to "root-123")),
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
                                mapOf(ROOT_ID_KEY to "root-123"),
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
                            fields = mapOf(ROOT_ID_KEY to "root-123"),
                        ),
                    needsEmbedding = true,
                )
            }

        @Test
        fun `searches with scoreMustBeAbove`() =
            runTest {
                assertScoreQuery(
                    request = QueryRequest(topic = "above topic", scoreMustBeAbove = 0.42f),
                    expectedScoreAbove = 0.42f,
                    expectedScoreBelow = null,
                    perform = sut::search,
                )
            }

        @Test
        fun `searches with scoreMustBeBelow`() =
            runTest {
                assertScoreQuery(
                    request = QueryRequest(topic = "below topic", scoreMustBeBelow = 0.13f),
                    expectedScoreAbove = null,
                    expectedScoreBelow = 0.13f,
                    perform = sut::search,
                )
            }

        @Test
        fun `returns empty queryOnId when VectorStore returns null queryOnId`() =
            runTest {
                setupEmbedding("test topic")
                setupVectorStore(createVectorStoreResults(queryOnId = null))

                val result = sut.search(QueryRequest(topic = "test topic"))

                assertEquals(expectedSearchResult(queryOnId = ""), result)
            }

        @Test
        fun `maps VectorStore entries to SearchResult entries`() =
            runTest {
                val vectorStoreEntries =
                    listOf(
                        VectorStore.QueryResult(
                            id = "entry-1",
                            text = "First entry",
                            similarity = 0.95f,
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to "author-1",
                                    AUTHOR_PUBLIC_KEY_KEY to "public-key-1",
                                    AUTHOR_SIGNATURE_KEY to "signature-1",
                                    SIGNATURE_VERSION_KEY to "1",
                                    RECEIVED_AT_KEY to "1609459200",
                                    ROOT_ID_KEY to "root-1",
                                ),
                        ),
                        VectorStore.QueryResult(
                            id = "entry-2",
                            text = "Second entry",
                            similarity = 0.80f,
                            fields =
                                mapOf(
                                    AUTHOR_GID_KEY to "author-2",
                                    AUTHOR_PUBLIC_KEY_KEY to "public-key-2",
                                    AUTHOR_SIGNATURE_KEY to "signature-2",
                                    SIGNATURE_VERSION_KEY to "1",
                                    RECEIVED_AT_KEY to "1609459300",
                                ),
                        ),
                    )
                setupEmbedding("test topic")
                setupVectorStore(createVectorStoreResults(entries = vectorStoreEntries))

                val result = sut.search(QueryRequest(topic = "test topic"))

                assertEquals(
                    expectedSearchResult(
                        entries =
                            listOf(
                                defaultSearchResultEntry(id = "entry-1", similarity = 0.95f),
                                defaultSearchResultEntry(id = "entry-2", similarity = 0.80f),
                            ),
                    ),
                    result,
                )
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
                        fields = mapOf(RECEIVED_AT_KEY to "1609459200"),
                    )
                setupVectorStore(createVectorStoreResults(entries = listOf(entryMissingAuthor)))

                val exception =
                    assertThrows<MissingVectorMetadataException> {
                        sut.search(QueryRequest(topic = "missing author topic"))
                    }
                assertEquals("missing-author-id", exception.entryId)
                assertEquals(AUTHOR_GID_KEY, exception.fieldName)
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
                        fields =
                            mapOf(
                                AUTHOR_GID_KEY to "author-without-received-at",
                                AUTHOR_PUBLIC_KEY_KEY to "public-key-without-received-at",
                                AUTHOR_SIGNATURE_KEY to "signature-without-received-at",
                                SIGNATURE_VERSION_KEY to "1",
                            ),
                    )
                setupVectorStore(createVectorStoreResults(entries = listOf(entryMissingReceivedAt)))

                val exception =
                    assertThrows<MissingVectorMetadataException> {
                        sut.search(QueryRequest(topic = "missing received at topic"))
                    }
                assertEquals("missing-received-at-id", exception.entryId)
                assertEquals(RECEIVED_AT_KEY, exception.fieldName)
            }
    }

    @Nested
    inner class SearchOn {
        private fun testSearchOn(
            queryId: String,
            expectedUUID: UUID?,
            expectedQueryOnId: String = testQueryOnId.toString(),
        ) = runTest {
            coEvery { vectorStore.queryOn(expectedUUID) } returns createVectorStoreResults()

            val result = sut.searchOn(QueryOnRequest(queryId))

            coVerify { vectorStore.queryOn(expectedUUID) }
            val expectedResult =
                if (expectedQueryOnId.isNotEmpty()) {
                    expectedSearchResult(queryOnId = expectedQueryOnId)
                } else {
                    expectedSearchResult(entries = emptyList(), queryOnId = "")
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
                val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                coEvery { vectorStore.queryOn(uuid) } returns createVectorStoreResults(queryOnId = null)

                val result = sut.searchOn(QueryOnRequest(uuid.toString()))

                assertEquals(expectedSearchResult(queryOnId = ""), result)
            }

        @Test
        fun `maps VectorStore entries to QueryResult entries correctly`() =
            runTest {
                val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
                val vectorStoreEntry =
                    VectorStore.QueryResult(
                        id = "query-on-entry",
                        text = "QueryOn content",
                        similarity = 0.85f,
                        fields =
                            mapOf(
                                AUTHOR_GID_KEY to "query-on-author",
                                AUTHOR_PUBLIC_KEY_KEY to "query-on-public-key",
                                AUTHOR_SIGNATURE_KEY to "query-on-signature",
                                SIGNATURE_VERSION_KEY to "1",
                                RECEIVED_AT_KEY to "1609459400",
                                ROOT_ID_KEY to "query-on-root",
                            ),
                    )
                coEvery { vectorStore.queryOn(uuid) } returns createVectorStoreResults(entries = listOf(vectorStoreEntry))

                val result = sut.searchOn(QueryOnRequest(uuid.toString()))

                assertEquals(
                    expectedSearchResult(
                        entries = listOf(defaultSearchResultEntry(id = "query-on-entry", similarity = 0.85f)),
                    ),
                    result,
                )
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
                                AUTHOR_PUBLIC_KEY_KEY to "public-key-1",
                                AUTHOR_SIGNATURE_KEY to "signature-1",
                                SIGNATURE_VERSION_KEY to "3",
                                RECEIVED_AT_KEY to "12345",
                            ),
                        ),
                        VectorStore.PostDump(
                            "id2",
                            "content2",
                            mapOf(
                                AUTHOR_GID_KEY to "author2",
                                AUTHOR_PUBLIC_KEY_KEY to "public-key-2",
                                AUTHOR_SIGNATURE_KEY to "signature-2",
                                SIGNATURE_VERSION_KEY to "5",
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
                                    authorPublicKey = it.fields[AUTHOR_PUBLIC_KEY_KEY]!!,
                                    authorSignature = it.fields[AUTHOR_SIGNATURE_KEY]!!,
                                    signatureVersion = it.fields[SIGNATURE_VERSION_KEY]!!.toInt(),
                                )
                            },
                    )
                assertEquals(expected, result)
            }

        @Test
        fun `missing mandatory fields raises exception`() =
            runTest {
                listOf(
                    AUTHOR_GID_KEY,
                    AUTHOR_PUBLIC_KEY_KEY,
                    AUTHOR_SIGNATURE_KEY,
                    SIGNATURE_VERSION_KEY,
                    RECEIVED_AT_KEY,
                ).forEach { missingField ->
                    val fetchResult =
                        listOf(
                            VectorStore.PostDump(
                                "id1",
                                "content1",
                                mapOf(
                                    AUTHOR_GID_KEY to "author1",
                                    AUTHOR_PUBLIC_KEY_KEY to "public-key-1",
                                    AUTHOR_SIGNATURE_KEY to "signature-1",
                                    SIGNATURE_VERSION_KEY to "1",
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
        fun `removes item when user is admin`() =
            runTest {
                val service = ContentService(embeddingService, vectorStore, hasherFactory, maxFetchItems, isAdmin = { it == "admin-gid" })
                coEvery { vectorStore.fetch(listOf(postId)) } returns
                    listOf(
                        VectorStore.PostDump(
                            postId,
                            "content",
                            mapOf(
                                AUTHOR_GID_KEY to "other-user",
                                ROOT_ID_KEY to "root-1",
                            ),
                        ),
                    )
                setupWriteAccess()

                val deletedIds = service.delete("admin-gid", postId)

                coVerify { transaction.remove(postId) }
                assertEquals(listOf(postId), deletedIds)
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
                                ROOT_ID_KEY to "root-1",
                            ),
                        ),
                    )
                setupWriteAccess()

                val deletedIds = sut.delete(testUserGID, postId)

                coVerify { transaction.remove(postId) }
                assertEquals(listOf(postId), deletedIds)
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

        @Test
        fun `removes replies when deleting root post`() =
            runTest {
                val rootId = "post-1"
                coEvery { vectorStore.fetch(listOf(rootId)) } returns
                    listOf(
                        VectorStore.PostDump(
                            rootId,
                            "content",
                            mapOf(
                                AUTHOR_GID_KEY to testUserGID,
                            ),
                        ),
                    )
                val repliesPage =
                    VectorStore.QueryResults(
                        resultsPage =
                            listOf(
                                VectorStore.QueryResult(
                                    id = "reply-1",
                                    text = "text-1",
                                    similarity = 1.0f,
                                    fields = mapOf(ROOT_ID_KEY to rootId),
                                ),
                                VectorStore.QueryResult(
                                    id = "reply-2",
                                    text = "text-2",
                                    similarity = 1.0f,
                                    fields = mapOf(ROOT_ID_KEY to rootId),
                                ),
                            ),
                        queryOnId = null,
                    )
                val emptyPage = VectorStore.QueryResults(resultsPage = emptyList(), queryOnId = null)
                coEvery {
                    vectorStore.query(
                        Query(fields = mapOf(ROOT_ID_KEY to rootId)),
                    )
                } returnsMany listOf(repliesPage, emptyPage)
                setupWriteAccess()

                val deletedIds = sut.delete(testUserGID, rootId)

                coVerify { transaction.remove(rootId) }
                coVerify { transaction.remove("reply-1") }
                coVerify { transaction.remove("reply-2") }
                assertEquals(listOf(rootId, "reply-1", "reply-2"), deletedIds)
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
                                    fields = mapOf(AUTHOR_GID_KEY to testUserGID, ROOT_ID_KEY to "post-1"),
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
                val repliesPage =
                    VectorStore.QueryResults(
                        resultsPage =
                            listOf(
                                VectorStore.QueryResult(
                                    id = "reply-2",
                                    text = "text-4",
                                    similarity = 1.0f,
                                    fields = mapOf(ROOT_ID_KEY to "post-1"),
                                ),
                            ),
                        queryOnId = null,
                    )
                coEvery {
                    vectorStore.query(
                        Query(fields = mapOf(ROOT_ID_KEY to "post-1")),
                    )
                } returnsMany listOf(repliesPage, emptyPage)
                coEvery {
                    vectorStore.query(
                        Query(fields = mapOf(ROOT_ID_KEY to "reply-1")),
                    )
                } returns emptyPage
                setupWriteAccess()

                val deletedIds = sut.deleteAllForUser(testUserGID)

                coVerify { transaction.remove("post-1") }
                coVerify { transaction.remove("post-2") }
                coVerify { transaction.remove("reply-1") }
                coVerify { transaction.remove("reply-2") }
                assertEquals(listOf("post-1", "post-2", "reply-2", "reply-1"), deletedIds)
            }
    }

    @Nested
    inner class Publish {
        @Test
        fun `throws BadRequest when text exceeds max length`() =
            runTest {
                val tooLongText = "a".repeat(1801)

                assertThrows<BadRequestException> {
                    sut.publish(testUserGID, PublishRequest(tooLongText), TEST_TIME, testPublicKey)
                }

                coVerify(exactly = 0) { embeddingService.embed(any()) }
            }

        @Test
        fun `publishes content with correct parameters`() =
            runTest {
                val content = "Hello world"
                val testHash = byteArrayOf(1, 2, 3, 4)
                setupEmbedding(content)
                setupHasher(testHash)
                setupWriteAccess()

                val contentId = sut.publish(testUserGID, PublishRequest(content), TEST_TIME, "")

                assertEquals("AQIDBA", contentId)
                coVerify { embeddingService.embed(content) }
                coVerify { hasherFactory() }
                coVerify { messageDigest.digest(content.toByteArray(Charsets.UTF_8)) }
                coVerify {
                    transaction.add(
                        id = "AQIDBA",
                        text = content,
                        fields =
                            match { fields ->
                                fields[AUTHOR_GID_KEY] == testUserGID &&
                                    fields[AUTHOR_PUBLIC_KEY_KEY] == "" &&
                                    fields[AUTHOR_SIGNATURE_KEY] == "" &&
                                    fields[SIGNATURE_VERSION_KEY] == "1" &&
                                    fields.containsKey(RECEIVED_AT_KEY) &&
                                    fields[RECEIVED_AT_KEY]?.toLongOrNull() != null
                            },
                        vector = testEmbedding,
                    )
                }
            }

        @Test
        fun `publishes content with signature metadata`() =
            runTest {
                val content = "Hello world"
                val testHash = byteArrayOf(1, 2, 3, 4)
                setupEmbedding(content)
                setupHasher(testHash)
                setupWriteAccess()

                sut.publish(
                    testUserGID,
                    PublishRequest(content, signature = testSignature, signatureVersion = 7),
                    TEST_TIME,
                    testPublicKey,
                )

                coVerify {
                    transaction.add(
                        id = "AQIDBA",
                        text = content,
                        fields =
                            mapOf(
                                AUTHOR_GID_KEY to testUserGID,
                                AUTHOR_PUBLIC_KEY_KEY to testPublicKey,
                                AUTHOR_SIGNATURE_KEY to testSignature,
                                SIGNATURE_VERSION_KEY to "7",
                                RECEIVED_AT_KEY to TEST_TIME.toString(),
                            ),
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
                    sut.reply(testUserGID, ReplyRequest(PublishRequest(tooLongText), "root-123"), TEST_TIME, testPublicKey)
                }

                coVerify(exactly = 0) { embeddingService.embed(any()) }
            }

        @Test
        fun `replies with rootId field set for top level root`() =
            runTest {
                val content = "Reply text"
                val testHash = byteArrayOf(5, 6, 7, 8)
                setupEmbedding(content)
                setupHasher(testHash)
                setupWriteAccess()
                coEvery { vectorStore.query(Query(id = "root-123")) } returns
                    createParentQueryResult("root-123", null)

                val contentId =
                    sut.reply(testUserGID, ReplyRequest(PublishRequest(content), "root-123"), TEST_TIME, testPublicKey)

                assertEquals("reply-id", contentId)
                coVerify { embeddingService.embed(content) }
                coVerify {
                    transaction.add(
                        id = "reply-id",
                        text = content,
                        fields =
                            mapOf(
                                AUTHOR_GID_KEY to testUserGID,
                                AUTHOR_PUBLIC_KEY_KEY to testPublicKey,
                                AUTHOR_SIGNATURE_KEY to "",
                                SIGNATURE_VERSION_KEY to "1",
                                RECEIVED_AT_KEY to "170000000",
                                ROOT_ID_KEY to "root-123",
                            ),
                        vector = testEmbedding,
                    )
                }
            }

        @Test
        fun `allows identical reply text more than once by generating different ids`() =
            runTest {
                val content = "Ja"
                val replyIds = ArrayDeque(listOf("reply-id-1", "reply-id-2"))
                val service =
                    ContentService(
                        embeddingService = embeddingService,
                        vectorStore = vectorStore,
                        hasherFactory = { MessageDigest.getInstance("SHA-256") },
                        maxFetchItems = maxFetchItems,
                        replyIdFactory = { replyIds.removeFirst() },
                    )
                setupEmbedding(content)
                setupWriteAccess()
                coEvery { vectorStore.query(Query(id = "root-123")) } returns
                    createParentQueryResult("root-123", null)

                val firstId = service.reply(testUserGID, ReplyRequest(PublishRequest(content), "root-123"), TEST_TIME, testPublicKey)
                val secondId = service.reply(testUserGID, ReplyRequest(PublishRequest(content), "root-123"), TEST_TIME, testPublicKey)

                assertTrue(firstId.isNotBlank())
                assertTrue(secondId.isNotBlank())
                assertTrue(firstId != secondId)
            }

        @Test
        fun `throws BadRequest when root is not found`() =
            runTest {
                val unknownRootId = "unknown-root"
                coEvery { vectorStore.query(Query(id = unknownRootId)) } returns
                    VectorStore.QueryResults(resultsPage = emptyList(), queryOnId = null)

                assertThrows<BadRequestException> {
                    sut.reply(testUserGID, ReplyRequest(PublishRequest("ignored"), unknownRootId), TEST_TIME, testPublicKey)
                }
            }

        @Test
        fun `throws BadRequest when replying to non-root`() =
            runTest {
                val content = "Reply text"
                val testHash = byteArrayOf(5, 6, 7, 8)
                setupEmbedding(content)
                setupHasher(testHash)
                coEvery { vectorStore.query(Query(id = "child-123")) } returns
                    createParentQueryResult("child-123", "root-123")

                assertThrows<BadRequestException> {
                    sut.reply(
                        testUserGID,
                        ReplyRequest(PublishRequest(content), "child-123"),
                        TEST_TIME,
                        testPublicKey,
                    )
                }
            }
    }

    private suspend fun assertScoreQuery(
        request: QueryRequest,
        expectedScoreAbove: Float?,
        expectedScoreBelow: Float?,
        perform: suspend (QueryRequest) -> Unit,
    ) {
        setupEmbedding(request.topic ?: "")
        var capturedQuery: VectorStore.Query? = null
        setupVectorStoreWithCapture(capture = { capturedQuery = it })

        perform(request)

        coVerify { vectorStore.query(any()) }
        with(requireNotNull(capturedQuery)) {
            assertEquals(true, vector?.contentEquals(testEmbedding))
            assertEquals(expectedScoreAbove, scoreMustBeAbove)
            assertEquals(expectedScoreBelow, scoreMustBeBelow)
            assertNull(fromTime)
            assertNull(toTime)
            assertEquals(mapOf(ROOT_ID_KEY to request.threadRootId), fields)
        }
    }

    private fun expectedQueryResult(
        entries: List<QueryResult.Entry> = listOf(defaultQueryResultEntry()),
        queryOnId: String = testQueryOnId.toString(),
    ) = QueryResult(entries = entries, queryOnId = queryOnId)

    private fun expectedSearchResult(
        entries: List<SearchResult.Entry> = listOf(defaultSearchResultEntry()),
        queryOnId: String = testQueryOnId.toString(),
    ) = SearchResult(entries = entries, queryOnId = queryOnId)

    private fun defaultQueryResultEntry(
        id: String = "test-id",
        content: String = "test content",
        similarity: Float = 0.9f,
        authorGID: String = "test-author",
        storedAt: Long = 1609459200L,
        rootId: String? = null,
        authorPublicKey: String = "test-public-key",
        authorSignature: String = "test-signature",
        signatureVersion: Int = 1,
    ) = QueryResult.Entry(
        id = id,
        content = content,
        similarity = similarity,
        authorGID = authorGID,
        storedAt = storedAt,
        rootId = rootId,
        authorPublicKey = authorPublicKey,
        authorSignature = authorSignature,
        signatureVersion = signatureVersion,
    )

    private fun defaultSearchResultEntry(
        id: String = "test-id",
        similarity: Float = 0.9f,
    ) = SearchResult.Entry(
        id = id,
        similarity = similarity,
    )

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
                            AUTHOR_GID_KEY to "test-author",
                            AUTHOR_PUBLIC_KEY_KEY to "test-public-key",
                            AUTHOR_SIGNATURE_KEY to "test-signature",
                            SIGNATURE_VERSION_KEY to "1",
                            RECEIVED_AT_KEY to "1609459200",
                        ),
                ),
            ),
    ) = VectorStore.QueryResults(
        queryOnId = queryOnId,
        resultsPage = entries,
    )

    private fun setupVectorStoreWithCapture(
        capture: (VectorStore.Query) -> Unit,
        result: VectorStore.QueryResults = createVectorStoreResults(),
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
