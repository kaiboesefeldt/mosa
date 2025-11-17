package straightway.mosa.client.util

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import straightway.mosa.client.logging.Logger

class CancellationAwareResultTest {
    @Test
    fun `getOrElse rethrows cancellations`() {
        val logger = RecordingLogger()

        assertFailsWith<CancellationException> {
            Result.failure<String>(CancellationException("stop"))
                .getOrElseLogOrThrowCancellation(logger, "ignored") { "fallback" }
        }
        assertTrue(logger.warnMessages.isEmpty())
    }

    @Test
    fun `getOrElse logs non cancellation errors`() {
        val logger = RecordingLogger()

        val value =
            Result.failure<String>(RuntimeException("boom"))
                .getOrElseLogOrThrowCancellation(logger, "store failed") { "fallback" }

        assertEquals("fallback", value)
        assertEquals(listOf("store failed"), logger.warnMessages)
    }

    @Test
    fun `onFailure logs non cancellations`() {
        val logger = RecordingLogger()

        Result.failure<Unit>(RuntimeException("boom"))
            .onFailureLogOrThrowCancellation(logger, "save failed")

        assertEquals(listOf("save failed"), logger.warnMessages)
    }

    @Test
    fun `onFailure rethrows cancellations`() {
        val logger = RecordingLogger()

        assertFailsWith<CancellationException> {
            Result.failure<Unit>(CancellationException("stop"))
                .onFailureLogOrThrowCancellation(logger, "ignored")
        }
        assertTrue(logger.warnMessages.isEmpty())
    }
}

private class RecordingLogger : Logger {
    val warnMessages = mutableListOf<String>()
    val errorMessages = mutableListOf<String>()

    override fun error(message: String, throwable: Throwable?) {
        errorMessages += message
    }

    override fun warn(message: String, throwable: Throwable?) {
        warnMessages += message
    }
}
