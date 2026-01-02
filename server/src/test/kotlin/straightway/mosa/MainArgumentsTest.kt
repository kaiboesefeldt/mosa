package straightway.mosa

import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli
import straightway.mosa.commandline.ServeCommand
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCli::class)
class MainArgumentsTest {
    @Test
    fun `store returns null when serve-source-maps argument omitted`() {
        val store = parseStore("serve")

        assertEquals(false, store?.getBoolean(ServerCommandLineArguments.SERVE_SOURCE_MAPS))
    }

    @Test
    fun `store returns true when serve-source-maps argument provided`() {
        val store = parseStore("serve", "--serve-source-maps")

        assertEquals(true, store?.getBoolean(ServerCommandLineArguments.SERVE_SOURCE_MAPS))
    }

    @Test
    fun `store returns default port when port argument omitted`() {
        val store = parseStore("serve")

        assertEquals(8080, store?.getInt(ServerCommandLineArguments.SERVER_PORT))
    }

    @Test
    fun `store returns provided port when port argument specified`() {
        val store = parseStore("serve", "--port", "9090")

        assertEquals(9090, store?.getInt(ServerCommandLineArguments.SERVER_PORT))
    }

    @Test
    fun `store requires tls by default`() {
        val store = parseStore("serve")

        assertEquals(false, store?.getBoolean(ServerCommandLineArguments.ALLOW_HTTP))
    }

    @Test
    fun `store disables tls requirement when flag set to false`() {
        val store = parseStore("serve", "--allow-http")

        assertEquals(true, store?.getBoolean(ServerCommandLineArguments.ALLOW_HTTP))
    }

    @Test
    fun `store returns default memory shedding range when argument omitted`() {
        val store = parseStore("serve")

        assertEquals(0.85, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_START))
        assertEquals(0.85, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_FULL))
    }

    @Test
    fun `store returns provided memory shedding range`() {
        val store =
            parseStore(
                "serve",
                "--memory-shedding-start",
                "0.7",
                "--memory-shedding-full",
                "0.9",
            )

        assertEquals(0.7, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_START))
        assertEquals(0.9, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_FULL))
    }

    private fun parseStore(vararg args: String): CommandLineArgumentStore? {
        var capturedStore: CommandLineArgumentStore? = null
        val parser = ArgParser("mosa")
        val serveCommand = ServeCommand(startServer = { capturedStore = it })

        parser.subcommands(serveCommand)
        parser.parse(args)

        return capturedStore
    }
}
