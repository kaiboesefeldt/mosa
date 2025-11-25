package straightway.mosa.client.identity

import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.w3c.dom.Document
import kotlin.js.Promise
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import web.clipboard.Clipboard

class ClipboardWriteAwaitTest {
    @Test
    fun `does nothing when result is not a promise`() = runTest {
        awaitClipboardWrite("done")
    }

    @Test
    fun `awaits promise result`() = runTest {
        var awaited = false
        val promise = Promise<Unit> { resolve, _ ->
            awaited = true
            resolve(Unit)
        }
        awaitClipboardWrite(promise)
        assertTrue(awaited)
    }

    @Test
    fun `writes through clipboard when available`() = runTest {
        var received = ""
        val clipboard = clipboardWithWrite { text ->
            received = text
            Promise.resolve(Unit)
        }

        val didCopy = copyTextToClipboard("profile", clipboard)

        assertTrue(didCopy)
        assertTrue(received == "profile")
    }

    @Test
    fun `falls back to execCommand when clipboard write is denied`() = runTest {
        val clipboard = clipboardWithWrite { throw js("new DOMException('denied')") }
        val execCommand = StubExecCommand()

        withExecCommand(execCommand) {
            val didCopy = copyTextToClipboard("fallback", clipboard)

            assertTrue(didCopy)
            assertTrue(execCommand.didCopy)
        }
    }

    @Test
    fun `returns false when no clipboard and execCommand fails`() = runTest {
        val execCommand = StubExecCommand(returnValue = false)

        val didCopy = withExecCommand(execCommand) { copyTextToClipboard("value", null) }

        assertFalse(didCopy)
    }
}

private fun clipboardWithWrite(write: (String) -> Any?): Clipboard {
    val clipboard = js("({})")
    clipboard.writeText = write
    return clipboard.unsafeCast<Clipboard>()
}

private class StubExecCommand(private val returnValue: Boolean = true) {
    var didCopy: Boolean = false
        private set

    fun install(on: Document) {
        on.asDynamic().execCommand = { command: String ->
            if (command == "copy") {
                didCopy = true
            }
            returnValue
        }
    }
}

private suspend fun <T> withExecCommand(execCommand: StubExecCommand, block: suspend () -> T): T {
    val previous = document.asDynamic().execCommand
    execCommand.install(document)
    try {
        return block()
    } finally {
        document.asDynamic().execCommand = previous
    }
}
