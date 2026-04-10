package straightway.mosa.config

import kotlinx.coroutines.test.runTest
import straightway.mosa.testutils.TestClock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class AutoRefreshingFileBackedContentTest {
    @Test
    fun `returns initial transformed value`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            writeContent(path, "hello".encodeToByteArray(), 1_000)
            val clock = TestClock(Instant.ofEpochMilli(0))
            val calls = AtomicInteger(0)

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    transformer = {
                        calls.incrementAndGet()
                        it.decodeToString()
                    },
                )

            assertEquals("hello", sut.get())
            assertEquals(1, calls.get())
        }

    @Test
    fun `throws when transformer fails on init`() {
        val path = createTempDirectory("mosa-test-").resolve("content.bin")
        writeContent(path, "boom".encodeToByteArray(), 1_000)
        val clock = TestClock(Instant.ofEpochMilli(0))

        assertFailsWith<IllegalStateException> {
            AutoRefreshingFileBackedContent(
                config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                clock = clock,
                transformer = { throw IllegalStateException("fail") },
            )
        }
    }

    @Test
    fun `does not refresh before interval even if file changed`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            writeContent(path, "first".encodeToByteArray(), 1_000)
            val clock = TestClock(Instant.ofEpochMilli(0))
            val calls = AtomicInteger(0)

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 10.seconds),
                    clock = clock,
                    transformer = {
                        calls.incrementAndGet()
                        it.decodeToString()
                    },
                )

            assertEquals("first", sut.get())

            writeContent(path, "second".encodeToByteArray(), 2_000)
            clock.advance(5.seconds)

            assertEquals("first", sut.get())
            assertEquals(1, calls.get())
        }

    @Test
    fun `refreshes after interval when file changed`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            writeContent(path, "first".encodeToByteArray(), 1_000)
            val clock = TestClock(Instant.ofEpochMilli(0))
            val calls = AtomicInteger(0)

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    transformer = {
                        calls.incrementAndGet()
                        it.decodeToString()
                    },
                )

            assertEquals("first", sut.get())

            writeContent(path, "second".encodeToByteArray(), 2_000)
            clock.advance(6.seconds)

            assertEquals("second", sut.get())
            assertEquals(2, calls.get())
        }

    @Test
    fun `force refresh reloads changed file before interval`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            writeContent(path, "first".encodeToByteArray(), 1_000)
            val clock = TestClock(Instant.ofEpochMilli(0))
            val calls = AtomicInteger(0)

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 10.seconds),
                    clock = clock,
                    transformer = {
                        calls.incrementAndGet()
                        it.decodeToString()
                    },
                )

            assertEquals("first", sut.get())

            writeContent(path, "second".encodeToByteArray(), 2_000)
            clock.advance(5.seconds)

            assertEquals("second", sut.get(forceRefresh = true))
            assertEquals(2, calls.get())
        }

    @Test
    fun `calls refresh callback after successful reload`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            writeContent(path, "first".encodeToByteArray(), 1_000)
            val clock = TestClock(Instant.ofEpochMilli(0))
            val refreshedPath = AtomicReference<Path?>(null)

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    onRefresh = { refreshedPath.set(it) },
                    transformer = { it.decodeToString() },
                )

            assertEquals("first", sut.get())
            assertNull(refreshedPath.get())

            writeContent(path, "second".encodeToByteArray(), 2_000)
            clock.advance(6.seconds)

            assertEquals("second", sut.get())
            assertEquals(path, refreshedPath.get())
        }

    @Test
    fun `keeps old value and reports errors during refresh`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            writeContent(path, "ok".encodeToByteArray(), 1_000)
            val clock = TestClock(Instant.ofEpochMilli(0))
            val errors = AtomicInteger(0)
            val calls = AtomicInteger(0)

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    onError = { errors.incrementAndGet() },
                    transformer = {
                        calls.incrementAndGet()
                        val text = it.decodeToString()
                        if (text == "bad") {
                            throw IllegalStateException("bad")
                        }
                        text
                    },
                )

            assertEquals("ok", sut.get())

            writeContent(path, "bad".encodeToByteArray(), 2_000)
            clock.advance(6.seconds)

            assertEquals("ok", sut.get())
            assertEquals(1, errors.get())
            assertEquals(2, calls.get())
        }

    @Test
    fun `does not call refresh callback when reload fails`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            writeContent(path, "ok".encodeToByteArray(), 1_000)
            val clock = TestClock(Instant.ofEpochMilli(0))
            val refreshedPath = AtomicReference<Path?>(null)

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    onRefresh = { refreshedPath.set(it) },
                    transformer = {
                        val text = it.decodeToString()
                        if (text == "bad") {
                            throw IllegalStateException("bad")
                        }
                        text
                    },
                )

            assertEquals("ok", sut.get())

            writeContent(path, "bad".encodeToByteArray(), 2_000)
            clock.advance(6.seconds)

            assertEquals("ok", sut.get())
            assertNull(refreshedPath.get())
        }

    @Test
    fun `returns null when file missing`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("missing.bin")
            val clock = TestClock(Instant.ofEpochMilli(0))
            val calls = AtomicInteger(0)

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    transformer = {
                        calls.incrementAndGet()
                        it.decodeToString()
                    },
                )

            assertNull(sut.get())
            assertEquals(0, calls.get())
        }

    @Test
    fun `refreshes when file appears after interval`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            val clock = TestClock(Instant.ofEpochMilli(0))

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    transformer = { it.decodeToString() },
                )

            assertNull(sut.get())

            writeContent(path, "now".encodeToByteArray(), 2_000)
            clock.advance(6.seconds)

            assertEquals("now", sut.get())
        }

    @Test
    fun `does not refresh when file appears before interval`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            val clock = TestClock(Instant.ofEpochMilli(0))

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    transformer = { it.decodeToString() },
                )

            assertNull(sut.get())

            writeContent(path, "too-soon".encodeToByteArray(), 2_000)
            clock.advance(2.seconds)

            assertNull(sut.get())
        }

    @Test
    fun `does not refresh when file is missing during refresh check`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            writeContent(path, "first".encodeToByteArray(), 1_000)
            val clock = TestClock(Instant.ofEpochMilli(0))
            val calls = AtomicInteger(0)

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    transformer = {
                        calls.incrementAndGet()
                        it.decodeToString()
                    },
                )

            assertEquals("first", sut.get())

            path.deleteIfExists()
            clock.advance(6.seconds)

            assertEquals("first", sut.get())
            assertEquals(1, calls.get())
        }

    @Test
    fun `toString returns null when value is null`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("missing.bin")
            val clock = TestClock(Instant.ofEpochMilli(0))

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    transformer = { it.decodeToString() },
                )

            assertEquals("null", sut.toString())
        }

    @Test
    fun `toString returns value when present`() =
        runTest {
            val path = createTempDirectory("mosa-test-").resolve("content.bin")
            writeContent(path, "hello".encodeToByteArray(), 1_000)
            val clock = TestClock(Instant.ofEpochMilli(0))

            val sut =
                AutoRefreshingFileBackedContent(
                    config = AutoRefreshingFileBackedContent.Config(path, 5.seconds),
                    clock = clock,
                    transformer = { it.decodeToString() },
                )

            assertEquals("hello", sut.toString())
        }

    private fun writeContent(
        path: Path,
        content: ByteArray,
        modifiedAtMillis: Long,
    ) {
        path.writeBytes(content)
        Files.setLastModifiedTime(path, FileTime.fromMillis(modifiedAtMillis))
    }
}
