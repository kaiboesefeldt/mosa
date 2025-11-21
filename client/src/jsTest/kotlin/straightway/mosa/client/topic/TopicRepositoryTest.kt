package straightway.mosa.client.topic

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import straightway.mosa.content.QueryResult
class TopicRepositoryTest {
    @Test
    fun `load returns topics from store`() = runTest {
        val store =
            RecordingTopicStore(
                initial =
                    listOf(
                        Topic("Erstes"),
                        Topic("Zweites"),
                    ),
            )
        val repository = TopicRepository(store, RecordingPostCache())

        val topics = repository.all()

        assertEquals(2, topics.size)
        assertEquals("Erstes", topics.first().title)
        assertTrue(store.saveInvocations.isEmpty())
    }

    @Test
    fun `add appends topic and persists order`() = runTest {
        val store = RecordingTopicStore()
        val repository = TopicRepository(store, RecordingPostCache())

        repository.add("Alpha")
        repository.add("Beta")

        val saved = store.saveInvocations.lastOrNull().orEmpty()
        assertEquals(listOf("Alpha", "Beta"), saved.map { it.title })
        assertEquals(listOf(null, null), saved.map { it.scoreMustBeAbove })
    }

    @Test
    fun `add ignores blank input`() = runTest {
        val store = RecordingTopicStore()
        val repository = TopicRepository(store, RecordingPostCache())

        repository.add("   ")

        assertTrue(store.saveInvocations.isEmpty())
    }

    @Test
    fun `remove deletes topic and persists remaining`() = runTest {
        val store =
            RecordingTopicStore(
                initial =
                    listOf(
                        Topic("Alpha"),
                        Topic("Beta"),
                    ),
            )
        val cache = RecordingPostCache()
        val repository = TopicRepository(store, cache)

        repository.remove(Topic("Alpha"))

        val saved = store.saveInvocations.lastOrNull().orEmpty()
        assertEquals(listOf("Beta"), saved.map { it.title })
        assertEquals(listOf("Alpha"), cache.clearInvocations)
    }

    @Test
    fun `clear is not invoked when topic kept`() = runTest {
        val store = RecordingTopicStore(initial = listOf(Topic("Alpha")))
        val cache = RecordingPostCache()
        val repository = TopicRepository(store, cache)

        repository.remove(Topic("Missing"))

        assertTrue(cache.clearInvocations.isEmpty())
    }

    @Test
    fun `apply filters overwrites filter state`() = runTest {
        val store =
            RecordingTopicStore(
                initial =
                    listOf(
                        Topic("Alpha"),
                        Topic("Beta"),
                    ),
            )
        val repository = TopicRepository(store, RecordingPostCache())

        repository.applyFilters(
            mapOf(
                "Alpha" to 0.95f,
                "Beta" to Float.NaN,
            ),
        )

        val saved = store.saveInvocations.lastOrNull().orEmpty()
        assertEquals(listOf(0.95f, null), saved.map { it.scoreMustBeAbove })
    }
}

private class RecordingTopicStore(
    private val initial: List<Topic> = emptyList(),
) : TopicStore {
    val saveInvocations = mutableListOf<List<Topic>>()

    override suspend fun load(): List<Topic> = initial

    override suspend fun save(topics: List<Topic>) {
        saveInvocations += topics
    }
}

private class RecordingPostCache : PostCache {
    val clearInvocations = mutableListOf<String>()

    override suspend fun snapshot(topic: String): PostCacheSnapshot =
        PostCacheSnapshot(emptyList(), null)

    override suspend fun append(topic: String, entries: List<QueryResult.Entry>): PostCacheSnapshot =
        PostCacheSnapshot(emptyList(), null)

    override suspend fun clear(topic: String) {
        clearInvocations += topic
    }
}
