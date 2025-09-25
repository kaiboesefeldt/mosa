package straightway.mosa.client.util

import kotlinx.browser.window

object Base64 {
    fun ByteArray.toBase64Url(): String {
        val binary = buildString(size) {
            for (byte in this@toBase64Url) {
                append((byte.toInt() and 0xFF).toChar())
            }
        }
        val b64 = window.btoa(binary)
        return b64.replace('+', '-').replace('/', '_').trimEnd('=')
    }

    fun fromBase64Url(b64url: String): ByteArray {
        val b64 = b64url.replace('-', '+').replace('_', '/')
        val pad = (4 - (b64.length % 4)) % 4
        val padded = b64 + "=".repeat(pad)
        val binary = window.atob(padded)
        val result = ByteArray(binary.length)
        for (index in result.indices) {
            result[index] = (binary[index].code and 0xFF).toByte()
        }
        return result
    }
}
