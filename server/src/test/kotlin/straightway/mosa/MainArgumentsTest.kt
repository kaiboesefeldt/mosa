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

    @Test
    fun `store returns default request duration shedding configuration when argument omitted`() {
        val store = parseStore("serve")

        assertEquals(200, store?.getInt(ServerCommandLineArguments.REQUEST_DURATION_MIN_SAMPLES))
        assertEquals(1.0, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_MIN_BASELINE_MS))
        assertEquals(0.15, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_RATIO_SLACK))
        assertEquals(2.0, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_FULL_SHEDDING_RATIO))
        assertEquals(0.2, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_ALPHA))
        assertEquals(0.2, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_DECAY_ALPHA))
        assertEquals(250, store?.getInt(ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_INTERVAL_MS))
    }

    @Test
    fun `store returns provided request duration shedding configuration`() {
        val store =
            parseStore(
                "serve",
                "--request-duration-shedding-min-samples",
                "150",
                "--request-duration-shedding-min-baseline-ms",
                "3.5",
                "--request-duration-shedding-ratio-slack",
                "0.25",
                "--request-duration-shedding-full-ratio",
                "2.5",
                "--request-duration-shedding-sample-alpha",
                "0.3",
                "--request-duration-shedding-decay-alpha",
                "0.4",
                "--request-duration-shedding-sample-interval-ms",
                "500",
            )

        assertEquals(150, store?.getInt(ServerCommandLineArguments.REQUEST_DURATION_MIN_SAMPLES))
        assertEquals(3.5, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_MIN_BASELINE_MS))
        assertEquals(0.25, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_RATIO_SLACK))
        assertEquals(2.5, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_FULL_SHEDDING_RATIO))
        assertEquals(0.3, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_ALPHA))
        assertEquals(0.4, store?.getDouble(ServerCommandLineArguments.REQUEST_DURATION_DECAY_ALPHA))
        assertEquals(500, store?.getInt(ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_INTERVAL_MS))
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
