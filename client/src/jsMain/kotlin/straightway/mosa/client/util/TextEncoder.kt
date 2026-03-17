package straightway.mosa.client.util

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

external class TextEncoder {
    fun encode(value: String): Uint8Array
}

fun textEncode(value: String): ByteArray {
    val encoded = TextEncoder().encode(value)
    val result = ByteArray(encoded.length)
    for (index in result.indices) {
        result[index] = encoded[index]
    }
    return result
}
