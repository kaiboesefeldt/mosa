package straightway.mosa.vectorstore

import io.ktor.server.plugins.BadRequestException
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import straightway.mosa.VectorStore
import straightway.mosa.VectorStore.Query
import straightway.mosa.testutils.TestClock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class LuceneLocalVectorStoreTest {
    private val test get() =
        object : AutoCloseable {
            val testClock = TestClock(Instant.ofEpochSecond(170000000L))
            val sut =
                LuceneLocalVectorStore.create(
                    directoryFactory = { ByteBuffersDirectory() },
                    clock = testClock,
                    maxResults = 10,
                    pageSize = 3,
                    maxNotFinishedQueries = 2,
                    vectorSize = 3,
                )

            override fun close() {
                sut.close()
            }
        }

    private operator fun <T : AutoCloseable> T.invoke(block: suspend T.() -> Unit): Unit =
        apply {
            runTest { block() }
        }.close()

    @Nested
    inner class Construction {
        @Test
        fun `non-positive maxNotFinishedQueries throws exception`() {
            assertThrows<RuntimeException> {
                LuceneLocalVectorStore.create(
                    directoryFactory = { ByteBuffersDirectory() },
                    clock = mockk(),
                    maxResults = 10,
                    pageSize = 3,
                    maxNotFinishedQueries = 0,
                    vectorSize = 3,
                )
            }
        }
    }

    @Nested
    inner class Add {
        @Test
        fun `adding invalid vector size throws exception`() =
            test {
                sut.writeAccess {
                    assertThrows<RuntimeException> {
                        add(
                            id = "id",
                            text = "Text",
                            fields = mapOf("Hello" to "world"),
                            vector = vector(1, 2),
                        )
                    }
                }
            }

        @Test
        fun `adding special name of field throws exception`() =
            test {
                sut.writeAccess {
                    listOf("id", "text", "vector").forEach { invalidName ->
                        assertThrows<RuntimeException> {
                            add(
                                id = "id",
                                text = "Text",
                                fields = mapOf(invalidName to "value"),
                                vector = vector,
                            )
                        }
                    }
                }
            }

        @Test
        fun `adding the same record twice in the same transaction throws exception`() =
            test {
                sut.writeAccess {
                    assertDoesNotThrow {
                        add(
                            id = "id",
                            text = "Text",
                            fields = emptyMap(),
                            vector = vector,
                        )
                    }
                    assertThrows<BadRequestException> {
                        add(
                            id = "id",
                            text = "Text",
                            fields = emptyMap(),
                            vector = vector,
                        )
                    }
                }
            }

        @Test
        fun `adding the same record twice in the different transactions throws exception`() =
            test {
                sut.writeAccess {
                    assertDoesNotThrow {
                        add(
                            id = "id",
                            text = "Text",
                            fields = emptyMap(),
                            vector = vector,
                        )
                    }
                }
                sut.writeAccess {
                    assertThrows<BadRequestException> {
                        add(
                            id = "id",
                            text = "Text",
                            fields = emptyMap(),
                            vector = vector,
                        )
                    }
                }
            }
    }

    @Nested
    inner class Query {
        @Test
        fun `happy path - add item and retrieve it again`() =
            test {
                sut.writeAccess {
                    add(
                        id = "id",
                        text = "Text",
                        fields = mapOf("Hello" to "world"),
                        vector = vector,
                    )
                }

                val queryResult = sut.query(Query(vector = vector))

                assertEquals(
                    listOf(
                        VectorStore.QueryResult(
                            id = "id",
                            text = "Text",
                            similarity = 1.0f,
                            fields = mapOf("Hello" to "world"),
                        ),
                    ),
                    queryResult.resultsPage,
                )
                assertNull(queryResult.queryOnId)
            }

        @Test
        fun `items are retrieved ordered by similarity`() =
            test {
                sut.writeAccess {
                    (0..2).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = emptyMap(),
                            vector = vector(1.0, it.toDouble(), 0.0),
                        )
                        testClock.advance(-1.days)
                    }
                }

                val searchResult = sut.query(Query(vector = vector(0.0, 1.0, 0.0)))

                assertEquals(
                    listOf("id2", "id1", "id0"),
                    searchResult.resultsPage.map { it.id },
                )
            }

        @Test
        fun `items can be filtered by doc id`() =
            test {
                sut.writeAccess {
                    (0..2).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = emptyMap(),
                            vector = vector(1.0, it.toDouble(), 0.0),
                        )
                    }
                }

                val searchResult =
                    sut.query(Query(id = "id1"))

                assertEquals(
                    listOf("id1"),
                    searchResult.resultsPage.map { it.id },
                )
            }

        @Test
        fun `items can be filtered by field value`() =
            test {
                sut.writeAccess {
                    (0..2).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = mapOf("tag" to "${it != 1}"),
                            vector = vector(1.0, it.toDouble(), 0.0),
                        )
                        testClock.advance(1.days)
                    }
                }

                val searchResult =
                    sut.query(
                        Query(fields = mapOf("tag" to "true")),
                    )

                assertEquals(
                    listOf("id2", "id0"),
                    searchResult.resultsPage.map { it.id },
                )
            }

        @Test
        fun `items can be filtered by min time`() =
            test {
                val itemTimes = mutableListOf<Instant>()
                sut.writeAccess {
                    (0..2).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = emptyMap(),
                            vector = vector(1.0, it.toDouble(), 0.0),
                        )
                        itemTimes.add(testClock.instant())
                        testClock.advance(1.days)
                    }
                }

                val searchResult =
                    sut.query(Query(fromTime = itemTimes[1] - 1.seconds.toJavaDuration()))

                assertEquals(
                    listOf("id2", "id1"),
                    searchResult.resultsPage.map { it.id },
                )
            }

        @Test
        fun `items can be filtered by max time`() =
            test {
                val itemTimes = mutableListOf<Instant>()
                sut.writeAccess {
                    (0..2).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = emptyMap(),
                            vector = vector(1.0, it.toDouble(), 0.0),
                        )
                        itemTimes.add(testClock.instant())
                        testClock.advance(1.days)
                    }
                }

                val searchResult =
                    sut.query(Query(toTime = itemTimes[1] + 1.seconds.toJavaDuration()))

                assertEquals(
                    listOf("id1", "id0"),
                    searchResult.resultsPage.map { it.id },
                )
            }

        @Test
        fun `items can be filtered by time range`() =
            test {
                val itemTimes = mutableListOf<Instant>()
                sut.writeAccess {
                    (0..2).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = emptyMap(),
                            vector = vector(1.0, it.toDouble(), 0.0),
                        )
                        itemTimes.add(testClock.instant())
                        testClock.advance(1.days)
                    }
                }

                val searchResult =
                    sut.query(
                        Query(
                            fromTime = itemTimes[1] - 1.seconds.toJavaDuration(),
                            toTime = itemTimes[1] + 1.seconds.toJavaDuration(),
                        ),
                    )

                assertEquals(
                    listOf("id1"),
                    searchResult.resultsPage.map { it.id },
                )
            }

        @Test
        fun `getting exactly as many results as pageSize yields null queryOnId`() =
            test {
                sut.writeAccess {
                    (1..3).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = mapOf("Hello" to "world"),
                            vector = vector,
                        )
                    }
                }

                val searchResult = sut.query(Query(vector = vector))

                assertEquals(3, searchResult.resultsPage.size)
                assertNull(searchResult.queryOnId)
            }

        @Test
        fun `values added in multiple transactions are available for searching`() =
            test {
                (1..3).forEach { numItems ->
                    sut.writeAccess {
                        add(
                            id = "id$numItems",
                            text = "Text",
                            fields = mapOf("Hello" to "world"),
                            vector = vector,
                        )
                    }

                    val searchResult = sut.query(Query(vector = vector))

                    assertEquals(
                        (1..numItems).map { "id$it" },
                        searchResult.resultsPage.map { it.id },
                    )
                    assertNull(searchResult.queryOnId)
                }
            }

        @Test
        fun `getting exactly one result more than pageSize yields a queryOnId`() =
            test {
                sut.writeAccess {
                    (1..4).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = mapOf("Hello" to "world"),
                            vector = vector,
                        )
                    }
                }

                val searchResult = sut.query(Query(vector = vector))

                assertEquals(3, searchResult.resultsPage.size)
                assertNotNull(searchResult.queryOnId)
            }

        @Test
        fun `searching initial index yields empty result`() =
            test {
                val searchResult = sut.query(Query(vector = vector))

                assertEquals(
                    emptyList(),
                    searchResult.resultsPage,
                )
                assertNull(searchResult.queryOnId)
            }

        @Test
        fun `searching for document id`() =
            test {
            }
    }

    @Nested
    inner class QueryOnKnn {
        @Test
        fun `searching on the next page of knn search results`() =
            test {
                sut.writeAccess {
                    (1..4).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = mapOf("Hello" to "world"),
                            vector = vector + vector(1.0, 0.0, 0.0),
                        )
                    }
                }

                val initialQueryResult = sut.query(Query(vector = vector))
                val queryOnResult = sut.queryOn(initialQueryResult.queryOnId)

                assertEquals(
                    listOf("id4"),
                    queryOnResult.resultsPage.map { it.id },
                )
                assertNull(queryOnResult.queryOnId)
            }

        @Test
        fun `searching on the pager after the next page of knn search results`() =
            test {
                sut.writeAccess {
                    (1..7).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = mapOf("Hello" to "world"),
                            vector = vector + vector(1.0, 0.0, 0.0),
                        )
                    }
                }

                val searchResult =
                    sut.queryOn(
                        sut
                            .queryOn(
                                sut.query(Query(vector = vector)).queryOnId,
                            ).queryOnId,
                    )

                assertEquals(
                    listOf("id7"),
                    searchResult.resultsPage.map { it.id },
                )
                assertNull(searchResult.queryOnId)
            }

        @Test
        fun `searching on with vanished searchOnId yields empty knn search results`() =
            test {
                sut.writeAccess {
                    add(
                        id = "id",
                        text = "Text",
                        fields = mapOf("Hello" to "world"),
                        vector = vector + vector(1.0, 0.0, 0.0),
                    )
                }

                val searchResult = sut.queryOn(UUID.randomUUID())

                assertEquals(emptyList(), searchResult.resultsPage)
                assertNull(searchResult.queryOnId)
            }

        @Test
        fun `searching on with null searchOnId yields empty knn search results`() =
            test {
                sut.writeAccess {
                    add(
                        id = "id",
                        text = "Text",
                        fields = mapOf("Hello" to "world"),
                        vector = vector + vector(1.0, 0.0, 0.0),
                    )
                }

                val searchResult = sut.queryOn(null)

                assertEquals(emptyList(), searchResult.resultsPage)
                assertNull(searchResult.queryOnId)
            }
    }

    @Nested
    inner class QueryOnField {
        @Test
        fun `searching on the next page of field search results`() =
            test {
                sut.writeAccess {
                    (1..4).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = mapOf("tag" to "value"),
                            vector = vector,
                        )
                        testClock.advance(1.days)
                    }
                }

                val initialQueryResult = sut.query(Query(fields = mapOf("tag" to "value")))
                val queryOnResult = sut.queryOn(initialQueryResult.queryOnId)

                assertEquals(
                    listOf("id1"),
                    queryOnResult.resultsPage.map { it.id },
                )
                assertNull(queryOnResult.queryOnId)
            }

        @Test
        fun `searching on the pager after the next page of field search results`() =
            test {
                sut.writeAccess {
                    (1..7).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = mapOf("tag" to "value"),
                            vector = vector + vector(1.0, 0.0, 0.0),
                        )
                    }
                }

                val searchResult =
                    sut.queryOn(
                        sut
                            .queryOn(
                                sut.query(Query(fields = mapOf("tag" to "value"))).queryOnId,
                            ).queryOnId,
                    )

                assertEquals(
                    listOf("id7"),
                    searchResult.resultsPage.map { it.id },
                )
                assertNull(searchResult.queryOnId)
            }
    }

    @Nested
    inner class Remove {
        @Test
        fun `removing not existing data does nothing`() =
            test {
                sut.writeAccess {
                    assertDoesNotThrow { remove("notExistingId") }
                }
            }

        @Test
        fun `removing data previously added in same transaction is not available for search`() =
            test {
                sut.writeAccess {
                    add(
                        id = "id",
                        text = "Text",
                        fields = mapOf("Hello" to "world"),
                        vector = vector + vector(1.0, 0.0, 0.0),
                    )
                    remove("id")
                }

                val searchResult = sut.query(Query(vector = vector))

                assertEquals(emptyList(), searchResult.resultsPage)
            }

        @Test
        fun `removing data previously added in another transaction is not available for search`() =
            test {
                sut.writeAccess {
                    add(
                        id = "id",
                        text = "Text",
                        fields = mapOf("Hello" to "world"),
                        vector = vector + vector(1.0, 0.0, 0.0),
                    )
                }
                sut.writeAccess {
                    remove("id")
                }

                val searchResult = sut.query(Query(vector = vector))

                assertEquals(emptyList(), searchResult.resultsPage)
            }
    }

    @Nested
    inner class LifeCycleOfQueryOnRecords {
        @Test
        fun `oldest searchOn record is cleaned up when searchOn space is expired`() =
            test {
                sut.writeAccess {
                    (1..4).forEach {
                        add(
                            id = "id$it",
                            text = "Text",
                            fields = mapOf("Hello" to "world"),
                            vector = vector + vector(1.0, 0.0, 0.0),
                        )
                    }
                }

                val searchResults = (1..3).map { sut.query(Query(vector = vector)) }
                val searchOnResults = searchResults.map { sut.queryOn(it.queryOnId) }

                searchResults.forEach {
                    assertNotNull(it.queryOnId)
                    assertTrue(it.resultsPage.any())
                }
                assertTrue(searchOnResults.first().resultsPage.isEmpty())
                searchOnResults.drop(1).forEach {
                    assertEquals(
                        listOf("id4"),
                        it.resultsPage.map { it.id },
                    )
                }
            }
    }

    @Nested
    inner class Close {
        @Test
        fun `duplicate call of close does nothing`() {
            test
                .apply {
                    assertDoesNotThrow { sut.close() }
                    assertDoesNotThrow { sut.close() }
                }
        }

        @Test
        fun `writeAccess after close throws exception`() {
            test
                .apply {
                    sut.close()
                    runTest {
                        assertThrows<RuntimeException> { sut.writeAccess { } }
                    }
                }
        }

        @Test
        fun `search after close throws exception`() {
            test
                .apply {
                    sut.close()
                    runTest {
                        assertThrows<RuntimeException> { sut.query(Query(vector = vector)) }
                    }
                }
        }

        @Test
        fun `searchOn after close throws exception`() {
            test
                .apply {
                    sut.close()
                    runTest {
                        assertThrows<RuntimeException> { sut.queryOn(UUID.randomUUID()) }
                    }
                }
        }
    }

    private companion object {
        fun vector(vararg components: Number) = FloatArray(components.size) { components[it].toFloat() }

        operator fun FloatArray.plus(other: FloatArray) = FloatArray(minOf(size, other.size)) { this[it] + other[it] }

        val vector = vector(1.0f, 2.0f, 4.0f)
    }
}
