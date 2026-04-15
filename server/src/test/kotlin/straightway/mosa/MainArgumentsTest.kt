package straightway.mosa

import io.mockk.every
import io.mockk.spyk
import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli
import straightway.mosa.commandline.ServeCommand
import straightway.mosa.content.ContentCommandLineOptions
import straightway.mosa.defense.DefenseCommandLineOptions
import straightway.mosa.network.NetworkCommandLineOptions
import straightway.mosa.webclient.WebClientCommandLineOptions.Companion.SERVE_SOURCE_MAPS
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCli::class)
class MainArgumentsTest {
    @Test
    fun `store returns null when serve-source-maps argument omitted`() {
        val store = parseStore("serve")

        assertEquals(false, store?.getBoolean(SERVE_SOURCE_MAPS))
    }

    @Test
    fun `store returns true when serve-source-maps argument provided`() {
        val store = parseStore("serve", "--serve-source-maps")

        assertEquals(true, store?.getBoolean(SERVE_SOURCE_MAPS))
    }

    @Test
    fun `store returns default port when port argument omitted`() {
        val store = parseStore("serve")

        assertEquals(8080, store?.getInt(NetworkCommandLineOptions.SERVER_PORT))
    }

    @Test
    fun `store returns provided port when port argument specified`() {
        val store = parseStore("serve", "--port", "9090")

        assertEquals(9090, store?.getInt(NetworkCommandLineOptions.SERVER_PORT))
    }

    @Test
    fun `store requires tls by default`() {
        val store = parseStore("serve")

        assertEquals(false, store?.getBoolean(NetworkCommandLineOptions.ALLOW_HTTP))
    }

    @Test
    fun `store disables tls requirement when flag set to false`() {
        val store = parseStore("serve", "--allow-http")

        assertEquals(true, store?.getBoolean(NetworkCommandLineOptions.ALLOW_HTTP))
    }

    @Test
    fun `store returns default memory shedding range when argument omitted`() {
        val store = parseStore("serve")

        assertEquals(0.9, store?.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_HEAP_START))
        assertEquals(0.98, store?.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_HEAP_FULL))
        assertEquals(0.98, store?.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_NONHEAP_START))
        assertEquals(0.999, store?.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_NONHEAP_FULL))
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

        assertEquals(0.7, store?.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_HEAP_START))
        assertEquals(0.9, store?.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_HEAP_FULL))
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

        assertEquals(0.7, store?.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_NONHEAP_START))
        assertEquals(0.9, store?.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_NONHEAP_FULL))
    }

    @Test
    fun `store returns default request duration shedding configuration when argument omitted`() {
        val store = parseStore("serve")

        assertEquals(200, store?.getInt(DefenseCommandLineOptions.REQUEST_DURATION_MIN_SAMPLES))
        assertEquals(1.0, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_MIN_BASELINE_MS))
        assertEquals(0.15, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_RATIO_SLACK))
        assertEquals(2.0, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_FULL_SHEDDING_RATIO))
        assertEquals(0.2, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_SAMPLE_ALPHA))
        assertEquals(0.2, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_DECAY_ALPHA))
        assertEquals(250, store?.getInt(DefenseCommandLineOptions.REQUEST_DURATION_SAMPLE_INTERVAL_MS))
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

        assertEquals(150, store?.getInt(DefenseCommandLineOptions.REQUEST_DURATION_MIN_SAMPLES))
        assertEquals(3.5, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_MIN_BASELINE_MS))
        assertEquals(0.25, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_RATIO_SLACK))
        assertEquals(2.5, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_FULL_SHEDDING_RATIO))
        assertEquals(0.3, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_SAMPLE_ALPHA))
        assertEquals(0.4, store?.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_DECAY_ALPHA))
        assertEquals(500, store?.getInt(DefenseCommandLineOptions.REQUEST_DURATION_SAMPLE_INTERVAL_MS))
    }

    @Test
    fun `store returns default rate limit penalties and jitter when argument omitted`() {
        val store = parseStore("serve")

        assertEquals(15, store?.getInt(DefenseCommandLineOptions.APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS))
        assertEquals(1, store?.getInt(DefenseCommandLineOptions.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS))
        assertEquals(5, store?.getInt(DefenseCommandLineOptions.AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS))
        assertEquals(10, store?.getInt(DefenseCommandLineOptions.IP_RATE_LIMIT_PENALTY_SECONDS))
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

        assertEquals(25, store?.getInt(DefenseCommandLineOptions.APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS))
        assertEquals(0, store?.getInt(DefenseCommandLineOptions.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS))
        assertEquals(8, store?.getInt(DefenseCommandLineOptions.AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS))
        assertEquals(12, store?.getInt(DefenseCommandLineOptions.IP_RATE_LIMIT_PENALTY_SECONDS))
    }

    @Test
    fun `store returns default request hardening configuration when argument omitted`() {
        val store = parseStore("serve")

        assertEquals(null, store?.getInt(DefenseCommandLineOptions.MAX_REQUEST_BODY_BYTES))
        assertEquals(20, store?.getInt(NetworkCommandLineOptions.REQUEST_READ_TIMEOUT_SECONDS))
        assertEquals(30, store?.getInt(NetworkCommandLineOptions.RESPONSE_WRITE_TIMEOUT_SECONDS))
        assertEquals(100, store?.getInt(ContentCommandLineOptions.MAX_FETCH_ITEMS))
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

        assertEquals(1024, store?.getInt(DefenseCommandLineOptions.MAX_REQUEST_BODY_BYTES))
        assertEquals(5, store?.getInt(NetworkCommandLineOptions.REQUEST_READ_TIMEOUT_SECONDS))
        assertEquals(12, store?.getInt(NetworkCommandLineOptions.RESPONSE_WRITE_TIMEOUT_SECONDS))
        assertEquals(250, store?.getInt(ContentCommandLineOptions.MAX_FETCH_ITEMS))
    }

    @Test
    fun `serve doc prints current top level runtime artifacts`() {
        val output =
            captureStdout {
                val parser = ArgParser("mosa")
                val serveCommand = spyk(ServeCommand())
                every { serveCommand.startMosaServer(any()) } answers {
                    throw IllegalStateException("serve should not start for --doc")
                }

                parser.subcommands(serveCommand)
                parser.parse(arrayOf("serve", "--doc"))
            }

        assertContains(output, "File name: mosa_imprint.yaml")
        assertContains(output, "File name: mosa_db.sqlite")
        assertContains(output, "File name: mosa_pod_keypair.pem")
        assertContains(output, "Directory name: mosa_lucene_index")
        assertFalse(output.contains("File name: mosa_admin_messages.json"), output)
        assertFalse(output.contains("File name: imprint.yaml"), output)
    }

    private fun parseStore(vararg args: String): CommandLineArgumentStore? {
        var capturedStore: CommandLineArgumentStore? = null
        val parser = ArgParser("mosa")
        val serveCommand = spyk(ServeCommand())
        every { serveCommand.startMosaServer(any()) } answers {
            capturedStore = firstArg()
        }

        parser.subcommands(serveCommand)
        parser.parse(args)

        return capturedStore
    }

    private fun captureStdout(block: () -> Unit): String {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        val printStream = PrintStream(output, true, Charsets.UTF_8)
        System.setOut(printStream)
        try {
            block()
        } finally {
            System.setOut(originalOut)
        }
        return output
            .toString(Charsets.UTF_8)
            .replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }
}
