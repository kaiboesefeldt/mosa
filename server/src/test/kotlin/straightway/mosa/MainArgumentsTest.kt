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

        assertEquals(0.9, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_START))
        assertEquals(0.98, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_FULL))
        assertEquals(0.98, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_START))
        assertEquals(0.999, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_FULL))
    }

    @Test
    fun `store returns provided heap memory shedding range`() {
        val store =
            parseStore(
                "serve",
                "--memory-shedding-heap-start",
                "0.7",
                "--memory-shedding-heap-full",
                "0.9",
            )

        assertEquals(0.7, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_START))
        assertEquals(0.9, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_FULL))
    }

    @Test
    fun `store returns provided nonheap memory shedding range`() {
        val store =
            parseStore(
                "serve",
                "--memory-shedding-nonheap-start",
                "0.7",
                "--memory-shedding-nonheap-full",
                "0.9",
            )

        assertEquals(0.7, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_START))
        assertEquals(0.9, store?.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_FULL))
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

    @Test
    fun `store returns default rate limit penalties and jitter when argument omitted`() {
        val store = parseStore("serve")

        assertEquals(15, store?.getInt(ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS))
        assertEquals(1, store?.getInt(ServerCommandLineArguments.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS))
        assertEquals(5, store?.getInt(ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS))
        assertEquals(10, store?.getInt(ServerCommandLineArguments.IP_RATE_LIMIT_PENALTY_SECONDS))
    }

    @Test
    fun `store returns provided rate limit penalties and jitter`() {
        val store =
            parseStore(
                "serve",
                "--approvegid-rate-limit-penalty-seconds",
                "25",
                "--rate-limit-retry-after-jitter-seconds",
                "0",
                "--authenticated-rate-limit-penalty-seconds",
                "8",
                "--ip-rate-limit-penalty-seconds",
                "12",
            )

        assertEquals(25, store?.getInt(ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS))
        assertEquals(0, store?.getInt(ServerCommandLineArguments.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS))
        assertEquals(8, store?.getInt(ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS))
        assertEquals(12, store?.getInt(ServerCommandLineArguments.IP_RATE_LIMIT_PENALTY_SECONDS))
    }

    @Test
    fun `store returns default request hardening configuration when argument omitted`() {
        val store = parseStore("serve")

        assertEquals(null, store?.getInt(ServerCommandLineArguments.MAX_REQUEST_BODY_BYTES))
        assertEquals(20, store?.getInt(ServerCommandLineArguments.REQUEST_READ_TIMEOUT_SECONDS))
        assertEquals(30, store?.getInt(ServerCommandLineArguments.RESPONSE_WRITE_TIMEOUT_SECONDS))
        assertEquals(100, store?.getInt(ServerCommandLineArguments.MAX_FETCH_ITEMS))
    }

    @Test
    fun `store returns provided request hardening configuration`() {
        val store =
            parseStore(
                "serve",
                "--max-request-body-bytes",
                "1024",
                "--request-read-timeout-seconds",
                "5",
                "--response-write-timeout-seconds",
                "12",
                "--max-fetch-items",
                "250",
            )

        assertEquals(1024, store?.getInt(ServerCommandLineArguments.MAX_REQUEST_BODY_BYTES))
        assertEquals(5, store?.getInt(ServerCommandLineArguments.REQUEST_READ_TIMEOUT_SECONDS))
        assertEquals(12, store?.getInt(ServerCommandLineArguments.RESPONSE_WRITE_TIMEOUT_SECONDS))
        assertEquals(250, store?.getInt(ServerCommandLineArguments.MAX_FETCH_ITEMS))
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
