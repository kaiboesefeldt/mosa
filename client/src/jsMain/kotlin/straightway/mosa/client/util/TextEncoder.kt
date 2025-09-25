package straightway.mosa.client.util

import kotlin.js.unsafeCast

external class TextEncoder {
    fun encode(s: String): dynamic
}

fun textEncode(s: String): ByteArray =
    TextEncoder().encode(s).unsafeCast<ByteArray>()
