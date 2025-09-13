package straightway.mosa.client

import org.khronos.webgl.Uint8Array
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Base64Test {
    @Test
    fun `encodes known bytes to base64url`() {
        val bytes = Uint8Array(intArrayOf(0xFF, 0xEE, 0xDD, 0xCC).map { it.toByte() }.toTypedArray())
        val encoded = Base64.run { bytes.toBase64Url() }
        assertEquals("_-7dzA", encoded)
    }

    @Test
    fun `roundtrip encode-decode`() {
        val bytes = Uint8Array(intArrayOf(0, 1, 2, 3, 250, 251, 252, 253, 254, 255).map { it.toByte() }.toTypedArray())
        val encoded = Base64.run { bytes.toBase64Url() }
        val decoded = Base64.fromBase64Url(encoded)
        // Verify same content
        val asArray = (0 until bytes.length).map { bytes.asDynamic()[it] as Int }.toTypedArray()
        val decArray = (0 until decoded.length).map { decoded.asDynamic()[it] as Int }.toTypedArray()
        assertContentEquals(asArray, decArray)
    }
}

