package straightway.mosa.client.util

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

    fun fromBase64Url(b64url: String): Uint8Array {
        val b64 = b64url.replace('-', '+').replace('_', '/')
        val pad = (4 - (b64.length % 4)) % 4
        val padded = b64 + "=".repeat(pad)
        val binary = window.atob(padded)
        val out = Uint8Array(binary.length)
        for (i in 0 until binary.length) {
            out.asDynamic()[i] = binary[i].code and 0xFF
        }
        return out
    }
}