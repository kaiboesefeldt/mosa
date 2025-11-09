package straightway.mosa.client.logging

import kotlin.js.console

interface Logger {
    fun error(message: String, throwable: Throwable? = null)
    fun warn(message: String, throwable: Throwable? = null)
}

class BrowserLogger : Logger {
    override fun error(message: String, throwable: Throwable?) {
        if (throwable != null) {
            console.error(message, throwable)
        } else {
            console.error(message)
        }
    }

    override fun warn(message: String, throwable: Throwable?) {
        if (throwable != null) {
            console.warn(message, throwable)
        } else {
            console.warn(message)
        }
    }
}
