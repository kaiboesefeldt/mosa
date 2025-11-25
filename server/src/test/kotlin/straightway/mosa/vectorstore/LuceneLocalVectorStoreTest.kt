package straightway.mosa.vectorstore

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Test
import straightway.mosa.VectorStore
import straightway.mosa.VectorStore.Query
import straightway.mosa.testutils.TestClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LuceneLocalVectorStoreTest {
    @Test
    fun `query returns empty results when index missing`() =
        runTest {
            newFixture(pageSize = 2, clock = Clock.fixed(Instant.ofEpochSecond(1), ZoneOffset.UTC)).use { fixture ->
                val result = fixture.store.query(Query())
                assertTrue(result.resultsPage.isEmpty())
                assertNull(result.queryOnId)
            }
        }

    @Test
    fun `query registers pagination with time sort when vector missing`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 1, clock = clock).use { fixture ->
                fixture.store.addDocument("doc-1", "first", mapOf("category" to "shared", "topic" to "news-1"), floatArrayOf(1f, 0f))
                clock.advance(1.seconds)
                fixture.store.addDocument("doc-2", "second", mapOf("category" to "shared", "topic" to "news-2"), floatArrayOf(0f, 1f))

                val result =
                    fixture.store.query(
                        Query(
                            fields = mapOf("category" to "shared"),
                        ),
                    )

                assertEquals(1, result.resultsPage.size)
                assertNotNull(result.queryOnId)
                val record = fixture.registry.get(result.queryOnId)
                assertNotNull(record)
                assertEquals(SORT_BY_TIME_ADDED, record.sort)
            }
        }

    @Test
    fun `query does not paginate when exactly one page available`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 2, clock = clock).use { fixture ->
                fixture.store.addDocument("doc-1", "first", mapOf("category" to "shared"), floatArrayOf(1f, 0f))
                fixture.store.addDocument("doc-2", "second", mapOf("category" to "shared"), floatArrayOf(0f, 1f))

                val result =
                    fixture.store.query(
                        Query(
                            fields = mapOf("category" to "shared"),
                        ),
                    )

                assertEquals(2, result.resultsPage.size)
                assertNull(result.queryOnId)
            }
        }

    @Test
    fun `query registers pagination without sort when vector provided`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 1, clock = clock).use { fixture ->
                fixture.store.addDocument("doc-1", "first", mapOf("category" to "shared", "topic" to "news-1"), floatArrayOf(1f, 0f))
                clock.advance(1.seconds)
                fixture.store.addDocument("doc-2", "second", mapOf("category" to "shared", "topic" to "news-2"), floatArrayOf(0f, 1f))

                val result =
                    fixture.store.query(
                        Query(
                            vector = floatArrayOf(1f, 0f),
                        ),
                    )

                assertEquals("doc-1", result.resultsPage.first().id)
                assertNotNull(result.queryOnId)
                val record = fixture.registry.get(result.queryOnId)
                assertNotNull(record)
                assertNull(record.sort)
            }
        }

    @Test
    fun `query registers pagination when more hits available`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 2, clock = clock).use { fixture ->
                repeat(5) { index ->
                    fixture.store.addDocument(
                        id = "bulk-${index + 1}",
                        text = "entry ${index + 1}",
                        fields = mapOf("category" to "bulk"),
                        vector = floatArrayOf(1f, 0f),
                    )
                    clock.advance(1.seconds)
                }

                val result =
                    fixture.store.query(
                        Query(
                            fields = mapOf("category" to "bulk"),
                        ),
                    )

                assertEquals(2, result.resultsPage.size)
                assertNotNull(result.queryOnId)
            }
        }

    @Test
    fun `query propagates unexpected errors`() =
        runTest {
            val failingPlanner = mockk<LuceneQueryPlanner>()
            every { failingPlanner.create(any()) } throws IllegalStateException("boom")
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 1, clock = clock, queryPlanner = failingPlanner).use { fixture ->
                fixture.store.addDocument("doc-1", "seed", emptyMap(), floatArrayOf(1f, 0f))
                assertFailsWith<IllegalStateException> {
                    fixture.store.query(Query())
                }
            }
        }

    @Test
    fun `dumpAll emits all documents and completes after last page`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 1, clock = clock).use { fixture ->
                fixture.store.addDocument("first-id", "first", emptyMap(), floatArrayOf(1f, 0f))
                clock.advance(1.seconds)
                fixture.store.addDocument("second-id", "second", emptyMap(), floatArrayOf(0f, 1f))

                val results =
                    withTimeout(2_000) {
                        fixture.store.dumpAll().toList()
                    }

                assertEquals(listOf("second-id", "first-id"), results.map { it.id })
            }
        }

    @Test
    fun `query omits pagination when result page smaller than capacity`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 2, clock = clock).use { fixture ->
                fixture.store.addDocument("doc-1", "single", mapOf("category" to "shared"), floatArrayOf(1f, 0f))

                val result =
                    fixture.store.query(
                        Query(
                            fields = mapOf("category" to "shared"),
                        ),
                    )

                assertEquals(1, result.resultsPage.size)
                assertNull(result.queryOnId)
            }
        }

    @Test
    fun `queryOn uses time sorting when registry stored sort`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 1, clock = clock).use { fixture ->
                fixture.store.addDocument("doc-1", "first", mapOf("category" to "shared"), floatArrayOf(1f, 0f))
                clock.advance(1.seconds)
                fixture.store.addDocument("doc-2", "second", mapOf("category" to "shared"), floatArrayOf(1f, 0f))
                clock.advance(1.seconds)
                fixture.store.addDocument("doc-3", "third", mapOf("category" to "shared"), floatArrayOf(1f, 0f))

                val initial =
                    fixture.store.query(
                        Query(
                            fields = mapOf("category" to "shared"),
                        ),
                    )
                val firstPageId = initial.queryOnId!!

                val followUp = fixture.store.queryOn(firstPageId)

                assertEquals("doc-2", followUp.resultsPage.first().id)
                assertNotNull(followUp.queryOnId)
                assertNull(fixture.registry.get(firstPageId))
            }
        }

    @Test
    fun `queryOn clears pagination after full final page`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 2, clock = clock).use { fixture ->
                repeat(4) { index ->
                    fixture.store.addDocument(
                        id = "full-${index + 1}",
                        text = "entry ${index + 1}",
                        fields = mapOf("category" to "full"),
                        vector = floatArrayOf(1f, 0f),
                    )
                    clock.advance(1.seconds)
                }

                val initial =
                    fixture.store.query(
                        Query(
                            fields = mapOf("category" to "full"),
                        ),
                    )
                val firstPageId = initial.queryOnId!!

                val followUp = fixture.store.queryOn(firstPageId)

                assertEquals(2, followUp.resultsPage.size)
                assertNull(followUp.queryOnId)
            }
        }

    @Test
    fun `queryOn stops pagination when final page incomplete`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 2, clock = clock).use { fixture ->
                fixture.store.addDocument("doc-1", "first", mapOf("category" to "shared"), floatArrayOf(1f, 0f))
                clock.advance(1.seconds)
                fixture.store.addDocument("doc-2", "second", mapOf("category" to "shared"), floatArrayOf(1f, 0f))
                clock.advance(1.seconds)
                fixture.store.addDocument("doc-3", "third", mapOf("category" to "shared"), floatArrayOf(1f, 0f))

                val initial =
                    fixture.store.query(
                        Query(
                            fields = mapOf("category" to "shared"),
                        ),
                    )
                val firstPageId = initial.queryOnId!!

                val followUp = fixture.store.queryOn(firstPageId)

                assertEquals(1, followUp.resultsPage.size)
                assertNull(followUp.queryOnId)
            }
        }

    @Test
    fun `queryOn uses knn continuation when registry stored vector query`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 1, clock = clock).use { fixture ->
                fixture.store.addDocument("doc-1", "north", mapOf("category" to "shared"), floatArrayOf(1f, 0f))
                clock.advance(1.seconds)
                fixture.store.addDocument("doc-2", "east", mapOf("category" to "shared"), floatArrayOf(0f, 1f))
                clock.advance(1.seconds)
                fixture.store.addDocument("doc-3", "north-east", mapOf("category" to "shared"), floatArrayOf(0.7f, 0.7f))

                val initial =
                    fixture.store.query(
                        Query(
                            vector = floatArrayOf(1f, 0f),
                        ),
                    )
                val firstPageId = initial.queryOnId!!

                val followUp = fixture.store.queryOn(firstPageId)
                val secondPageId = followUp.queryOnId!!
                val finalPage = fixture.store.queryOn(secondPageId)
                val trailingId = finalPage.queryOnId

                assertEquals("doc-3", followUp.resultsPage.first().id)
                assertEquals("doc-2", finalPage.resultsPage.first().id)
                assertNull(fixture.registry.get(firstPageId))
                assertNull(fixture.registry.get(secondPageId))
                if (trailingId != null) {
                    val trailingPage = fixture.store.queryOn(trailingId)
                    assertTrue(trailingPage.resultsPage.isEmpty())
                    assertNull(trailingPage.queryOnId)
                    assertNull(fixture.registry.get(trailingId))
                }
            }
        }

    @Test
    fun `queryOn returns empty result when id missing`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            newFixture(pageSize = 1, clock = clock).use { fixture ->
                val result = fixture.store.queryOn(null)
                assertTrue(result.resultsPage.isEmpty())
                assertNull(result.queryOnId)

                val randomResult = fixture.store.queryOn(UUID.randomUUID())
                assertTrue(randomResult.resultsPage.isEmpty())
                assertNull(randomResult.queryOnId)
            }
        }

    @Test
    fun `operations fail after close`() =
        runTest {
            val clock = TestClock(Instant.ofEpochSecond(1))
            val fixture = newFixture(pageSize = 1, clock = clock)
            fixture.store.close()

            assertFailsWith<RuntimeException> {
                fixture.store.writeAccess { }
            }
            assertFailsWith<RuntimeException> {
                fixture.store.query(Query())
            }
            assertFailsWith<RuntimeException> {
                fixture.store.queryOn(UUID.randomUUID())
            }
            fixture.close()
        }

    private fun newFixture(
        pageSize: Int,
        clock: Clock,
        vectorSize: Int = 2,
        queryPlanner: LuceneQueryPlanner = LuceneQueryPlanner(maxResults = 10),
    ): StoreFixture {
        val registry = QueryOnRegistry(pageSize = pageSize, maxNotFinishedQueries = 10)
        val indexAccess = LuceneIndexAccess { ByteBuffersDirectory() }
        val store =
            LuceneLocalVectorStore.create(
                indexAccess = indexAccess,
                paginationRegistry = registry,
                queryPlanner = queryPlanner,
                clock = clock,
                vectorSize = vectorSize,
            ) as LuceneLocalVectorStore
        return StoreFixture(store, registry)
    }

    private class StoreFixture(
        val store: LuceneLocalVectorStore,
        val registry: QueryOnRegistry,
    ) : AutoCloseable {
        override fun close() {
            store.close()
        }
    }

    private suspend fun VectorStore.addDocument(
        id: String,
        text: String,
        fields: Map<String, String>,
        vector: FloatArray,
    ) {
        writeAccess {
            add(id, text, fields, vector)
        }
    }
}
