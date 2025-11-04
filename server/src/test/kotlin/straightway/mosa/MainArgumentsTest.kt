package straightway.mosa

import kotlin.test.Test
import kotlin.test.assertEquals

class MainArgumentsTest {
    @Test
    fun `store returns null when serve-source-maps argument omitted`() {
        val store = buildCommandLineArgumentStore(emptyArray())

        assertEquals(false, store.get(ServerCommandLineArguments.SERVE_SOURCE_MAPS))
    }

    @Test
    fun `store returns true when serve-source-maps argument provided`() {
        val store = buildCommandLineArgumentStore(arrayOf("--serve-source-maps"))

        assertEquals(true, store.get(ServerCommandLineArguments.SERVE_SOURCE_MAPS))
    }
}
