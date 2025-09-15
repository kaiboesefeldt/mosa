package straightway.mosa.client

import org.khronos.webgl.Uint8Array

external class TextEncoder {
    fun encode(s: String): Uint8Array
}

fun textEncode(s: String): Uint8Array =
    TextEncoder().encode(s)
