package straightway.mosa

import kotlin.test.Test
import kotlin.test.assertEquals

class MainArgumentsTest {
    @Test
    fun `store returns null when serve-source-maps argument omitted`() {
        val store = buildCommandLineArgumentStore(emptyArray())

        assertEquals(false, store.getBoolean(ServerCommandLineArguments.SERVE_SOURCE_MAPS))
    }

    @Test
    fun `store returns true when serve-source-maps argument provided`() {
        val store = buildCommandLineArgumentStore(arrayOf("--serve-source-maps"))

        assertEquals(true, store.getBoolean(ServerCommandLineArguments.SERVE_SOURCE_MAPS))
    }

    @Test
    fun `store returns default port when port argument omitted`() {
        val store = buildCommandLineArgumentStore(emptyArray())

        assertEquals(8080, store.getInt(ServerCommandLineArguments.SERVER_PORT))
    }

    @Test
    fun `store returns provided port when port argument specified`() {
        val store = buildCommandLineArgumentStore(arrayOf("--port", "9090"))

        assertEquals(9090, store.getInt(ServerCommandLineArguments.SERVER_PORT))
    }

    @Test
    fun `store requires tls by default`() {
        val store = buildCommandLineArgumentStore(emptyArray())

        assertEquals(false, store.getBoolean(ServerCommandLineArguments.ALLOW_HTTP))
    }

    @Test
    fun `store disables tls requirement when flag set to false`() {
        val store = buildCommandLineArgumentStore(arrayOf("--allow-http"))

        assertEquals(true, store.getBoolean(ServerCommandLineArguments.ALLOW_HTTP))
    }
}
