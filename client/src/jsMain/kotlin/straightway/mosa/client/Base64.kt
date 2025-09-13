package straightway.mosa.client

import kotlinx.browser.window
import org.khronos.webgl.Uint8Array

object Base64 {
    fun Uint8Array.toBase64Url(): String {
        val len = length
        val sb = StringBuilder(len)
        var i = 0
        while (i < len) {
            val v = (asDynamic()[i] as Int)
            sb.append(v.toChar())
            i++
        }
        val binary = sb.toString()
        val b64 = window.btoa(binary)
        return b64.replace('+', '-').replace('/', '_').trimEnd('=')
    }
}