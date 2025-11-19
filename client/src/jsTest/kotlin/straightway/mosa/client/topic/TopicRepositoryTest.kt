package straightway.mosa.client.topic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

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
        val repository = TopicRepository(store)

        val topics = repository.all()

        assertEquals(2, topics.size)
        assertEquals("Erstes", topics.first().title)
        assertTrue(store.saveInvocations.isEmpty())
    }

    @Test
    fun `add appends topic and persists order`() = runTest {
        val store = RecordingTopicStore()
        val repository = TopicRepository(store)

        repository.add("Alpha")
        repository.add("Beta")

        val saved = store.saveInvocations.lastOrNull().orEmpty()
        assertEquals(listOf("Alpha", "Beta"), saved.map { it.title })
        assertEquals(listOf(null, null), saved.map { it.filter })
    }

    @Test
    fun `add ignores blank input`() = runTest {
        val store = RecordingTopicStore()
        val repository = TopicRepository(store)

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
        val repository = TopicRepository(store)

        repository.remove(Topic("Alpha"))

        val saved = store.saveInvocations.lastOrNull().orEmpty()
        assertEquals(listOf("Beta"), saved.map { it.title })
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
        val repository = TopicRepository(store)

        repository.applyFilters(
            mapOf(
                "Alpha" to "id-1",
                "Beta" to "  ",
            ),
        )

        val saved = store.saveInvocations.lastOrNull().orEmpty()
        assertEquals(listOf("id-1", null), saved.map { it.filter })
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
