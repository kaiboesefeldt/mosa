package straightway.mosa.defense

import straightway.mosa.CommandLineArgumentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandLineArgumentStoreTest {
    @Test
    fun `getBoolean returns stored boolean`() {
        val store = CommandLineArgumentStore.Companion.from { if (it == "flag") true else null }

        assertEquals(true, store.getBoolean("flag"))
    }

    @Test
    fun `getInt returns null when stored value is not an int`() {
        val store = CommandLineArgumentStore.Companion.from { if (it == "flag") true else null }

        assertNull(store.getInt("flag"))
    }

    @Test
    fun `getString returns stored string`() {
        val store = CommandLineArgumentStore.Companion.from { if (it == "name") "value" else null }

        assertEquals("value", store.getString("name"))
    }

    @Test
    fun `getDouble returns stored double`() {
        val store = CommandLineArgumentStore.Companion.from { if (it == "value") 1.23 else null }

        assertEquals(1.23, store.getDouble("value"))
    }
}
