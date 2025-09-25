package straightway.mosa.client.util

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView

fun ArrayBuffer.toByteArray(): ByteArray {
    val view = DataView(this)
    return ByteArray(view.byteLength) { view.getInt8(it) }
}
