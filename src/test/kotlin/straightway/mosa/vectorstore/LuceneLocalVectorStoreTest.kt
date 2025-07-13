package straightway.mosa.vectorstore

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.runBlocking
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import straightway.mosa.VectorStore
import java.util.UUID
import kotlin.test.assertEquals

class LuceneLocalVectorStoreTest {
    private val test get() =
        object : AutoCloseable {
            val sut =
                LuceneLocalVectorStore(
                    directoryFactory = { ByteBuffersDirectory() },
                    maxResults = 10,
                    pageSize = 3,
                    maxNotFinishedQueries = 2,
                    vectorSize = 3,
                )

            override fun close() {
                sut.close()
            }
        }

    @Nested
    inner class Construction {
        @Test
        fun `non-positive maxNotFinishedQueries throws exception`() {
            assertThrows<RuntimeException> {
                LuceneLocalVectorStore(
                    directoryFactory = { ByteBuffersDirectory() },
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
        fun `adding invalid vector size throws exception`() {
            test
                .apply {
                    runBlocking {
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
                }
        }

        @Test
        fun `adding special name of field throws exception`() {
            test
                .apply {
                    runBlocking {
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
                }
        }

        @Test
        fun `adding the same record twice in the same transaction throws exception`() {
            test
                .apply {
                    runBlocking {
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
                }
        }

        @Test
        fun `adding the same record twice in the different transactions throws exception`() {
            test
                .apply {
                    runBlocking {
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
        }
    }

    @Nested
    inner class Query {
        @Test
        fun `happy path - add item and retrieve it again`() =
            test
                .apply {
                    runBlocking {
                        sut.writeAccess {
                            add(
                                id = "id",
                                text = "Text",
                                fields = mapOf("Hello" to "world"),
                                vector = vector,
                            )
                        }

                        val queryResult = sut.query(vector)

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
                }.close()

        @Test
        fun `getting exactly as many results as pageSize yields null queryOnId`() =
            test
                .apply {
                    runBlocking {
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

                        val searchResult = sut.query(vector)

                        assertEquals(3, searchResult.resultsPage.size)
                        assertNull(searchResult.queryOnId)
                    }
                }.close()

        @Test
        fun `values added in multiple transactions are available for searching`() =
            test
                .apply {
                    runBlocking {
                        (1..3).forEach { numItems ->
                            sut.writeAccess {
                                add(
                                    id = "id$numItems",
                                    text = "Text",
                                    fields = mapOf("Hello" to "world"),
                                    vector = vector,
                                )
                            }

                            val searchResult = sut.query(vector)

                            assertEquals(
                                (1..numItems).map { "id$it" },
                                searchResult.resultsPage.map { it.id },
                            )
                            assertNull(searchResult.queryOnId)
                        }
                    }
                }.close()

        @Test
        fun `getting exactly one result more than pageSize yields a queryOnId`() =
            test
                .apply {
                    runBlocking {
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

                        val searchResult = sut.query(vector)

                        assertEquals(3, searchResult.resultsPage.size)
                        assertNotNull(searchResult.queryOnId)
                    }
                }.close()

        @Test
        fun `searching initial index yields empty result`() =
            test
                .apply {
                    runBlocking {
                        val searchResult = sut.query(vector)

                        assertEquals(
                            emptyList(),
                            searchResult.resultsPage,
                        )
                        assertNull(searchResult.queryOnId)
                    }
                }.close()

        @Test
        fun `searching for not existing topic vector yields empty result`() =
            test
                .apply {
                    runBlocking {
                        sut.writeAccess {
                            add(
                                id = "id",
                                text = "Text",
                                fields = mapOf("Hello" to "world"),
                                vector = vector,
                            )
                        }

                        val searchResult =
                            sut.query(vector.map { -it }.toFloatArray())

                        assertEquals(
                            listOf(
                                VectorStore.QueryResult(
                                    id = "id",
                                    text = "Text",
                                    similarity = 0.0f,
                                    fields = mapOf("Hello" to "world"),
                                ),
                            ),
                            searchResult.resultsPage,
                        )
                        assertNull(searchResult.queryOnId)
                    }
                }.close()
    }

    @Nested
    inner class QueryOn {
        @Test
        fun `searching on the next page of search results`() =
            test
                .apply {
                    runBlocking {
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

                        val searchResult =
                            sut.queryOn(sut.query(vector).queryOnId)

                        assertEquals(
                            listOf("id4"),
                            searchResult.resultsPage.map { it.id },
                        )
                        assertNull(searchResult.queryOnId)
                    }
                }.close()

        @Test
        fun `searching on the pager after the next page of search results`() =
            test
                .apply {
                    runBlocking {
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
                                        sut.query(vector).queryOnId,
                                    ).queryOnId,
                            )

                        assertEquals(
                            listOf("id7"),
                            searchResult.resultsPage.map { it.id },
                        )
                        assertNull(searchResult.queryOnId)
                    }
                }.close()

        @Test
        fun `searching on with vanished searchOnId yields empty search results`() =
            test
                .apply {
                    runBlocking {
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
                }.close()

        @Test
        fun `searching on with null searchOnId yields empty search results`() =
            test
                .apply {
                    runBlocking {
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
                }.close()
    }

    @Nested
    inner class Remove {
        @Test
        fun `removing not existing data does nothing`() =
            test
                .apply {
                    runBlocking {
                        sut.writeAccess {
                            assertDoesNotThrow { remove("notExistingId") }
                        }
                    }
                }.close()

        @Test
        fun `removing data previously added in same transaction is not available for search`() =
            test
                .apply {
                    runBlocking {
                        sut.writeAccess {
                            add(
                                id = "id",
                                text = "Text",
                                fields = mapOf("Hello" to "world"),
                                vector = vector + vector(1.0, 0.0, 0.0),
                            )
                            remove("id")
                        }

                        val searchResult = sut.query(vector)

                        assertEquals(emptyList(), searchResult.resultsPage)
                    }
                }.close()

        @Test
        fun `removing data previously added in another transaction is not available for search`() =
            test
                .apply {
                    runBlocking {
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

                        val searchResult = sut.query(vector)

                        assertEquals(emptyList(), searchResult.resultsPage)
                    }
                }.close()
    }

    @Nested
    inner class LifeCycleOfQueryOnRecords {
        @Test
        fun `oldest searchOn record is cleaned up when searchOn space is expired`() =
            test
                .apply {
                    runBlocking {
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

                        val searchResults = (1..3).map { sut.query(vector) }
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
                }.close()
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
                    runBlocking {
                        assertThrows<RuntimeException> { sut.writeAccess { } }
                    }
                }
        }

        @Test
        fun `search after close throws exception`() {
            test
                .apply {
                    sut.close()
                    runBlocking {
                        assertThrows<RuntimeException> { sut.query(vector) }
                    }
                }
        }

        @Test
        fun `searchOn after close throws exception`() {
            test
                .apply {
                    sut.close()
                    runBlocking {
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
