package straightway.mosa.client.topic

import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Storage

class TopicFilterStoreTest {
    @Test
    fun `saves and loads filters`() {
        val storage = createStorage()
        val store = BrowserTopicFilterStore { storage }

        store.save(mapOf("Topic A" to "id-1", "Topic B" to "id-2"))
        val loaded = store.load()

        assertEquals(mapOf("Topic A" to "id-1", "Topic B" to "id-2"), loaded)
    }

    @Test
    fun `ignores blank keys and values`() {
        val storage = createStorage()
        val store = BrowserTopicFilterStore { storage }

        store.save(mapOf(" " to "id", "Topic" to "", "Valid" to "id-3"))
        val loaded = store.load()

        assertEquals(mapOf("Valid" to "id-3"), loaded)
    }

    @Test
    fun `load returns empty for invalid payload`() {
        val storage = createStorage()
        storage.setItem("topic-filters", "???")
        val store = BrowserTopicFilterStore { storage }

        val loaded = store.load()

        assertTrue(loaded.isEmpty())
    }

    private fun createStorage(): Storage =
        js(
            """
            (function() {
                var data = {};
                var storage = {
                    length: 0,
                    clear: function() {
                        data = {};
                        storage.length = 0;
                    },
                    getItem: function(key) {
                        return Object.prototype.hasOwnProperty.call(data, key) ? data[key] : null;
                    },
                    key: function(index) {
                        var keys = Object.keys(data);
                        return (index >= 0 && index < keys.length) ? keys[index] : null;
                    },
                    removeItem: function(key) {
                        if (Object.prototype.hasOwnProperty.call(data, key)) {
                            delete data[key];
                            storage.length = Object.keys(data).length;
                        }
                    },
                    setItem: function(key, value) {
                        data[key] = value;
                        storage.length = Object.keys(data).length;
                    }
                };
                return storage;
            })()
            """
        ).unsafeCast<Storage>()
}
