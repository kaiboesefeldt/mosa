package straightway.mosa.client.logging

import kotlin.js.jsTypeOf
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BrowserLoggerTest {
    @Test
    fun `logs error without throwable`() = withConsoleSpy { spy ->
        val logger = BrowserLogger()

        logger.error("boom")

        assertEquals(listOf(ConsoleCall("error", listOf("boom"))), spy.calls())
    }

    @Test
    fun `logs error with throwable`() = withConsoleSpy { spy ->
        val logger = BrowserLogger()
        val throwable = IllegalStateException("kaputt")

        logger.error("boom", throwable)

        val call = spy.calls().single()
        assertEquals("error", call.method)
        assertEquals("boom", call.arguments.first())
        assertSame(throwable, call.arguments.last())
    }

    @Test
    fun `logs warning without throwable`() = withConsoleSpy { spy ->
        val logger = BrowserLogger()

        logger.warn("achtung")

        assertEquals(listOf(ConsoleCall("warn", listOf("achtung"))), spy.calls())
    }

    @Test
    fun `logs warning with throwable`() = withConsoleSpy { spy ->
        val logger = BrowserLogger()
        val throwable = IllegalArgumentException("verifiziert")

        logger.warn("achtung", throwable)

        val call = spy.calls().single()
        assertEquals("warn", call.method)
        assertEquals("achtung", call.arguments.first())
        assertSame(throwable, call.arguments.last())
    }
}

private data class ConsoleCall(val method: String, val arguments: List<Any?>)

private fun withConsoleSpy(block: (ConsoleSpy) -> Unit) {
    val global = js("globalThis").unsafeCast<dynamic>()
    val hadConsole = js("Object.prototype.hasOwnProperty.call(globalThis, 'console')").unsafeCast<Boolean>()
    val original = global.console
    val spy = ConsoleSpy()
    global.console = spy.console
    try {
        block(spy)
    } finally {
        if (hadConsole) {
            global.console = original
        } else {
            js("delete globalThis.console")
        }
    }
}

private class ConsoleSpy {
    private val recordedCalls = mutableListOf<ConsoleCall>()

    val console: dynamic = js("({})")

    init {
        console.error = { message: Any?, throwable: Any? ->
            record("error", message, throwable)
        }
        console.warn = { message: Any?, throwable: Any? ->
            record("warn", message, throwable)
        }
    }

    fun calls(): List<ConsoleCall> = recordedCalls.toList()

    private fun record(method: String, message: Any?, throwable: Any?) {
        val arguments = mutableListOf(message)
        if (jsTypeOf(throwable) != "undefined") {
            arguments += throwable
        }
        recordedCalls += ConsoleCall(method, arguments)
    }
}
