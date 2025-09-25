package straightway.mosa.client.util

import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Base64Test {
    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @Test
    fun `encodes known bytes to base64url`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xEE.toByte(), 0xDD.toByte(), 0xCC.toByte())
        val encoded = Base64.run { bytes.toBase64Url() }
        assertEquals("_-7dzA", encoded)
    }

    @Test
    fun `roundtrip encode-decode`() {
        val bytes =
            byteArrayOf(0.toByte(), 1.toByte(), 2.toByte(), 3.toByte(), 250.toByte(), 251.toByte(), 252.toByte(), 253.toByte(), 254.toByte(), 255.toByte())
        val encoded = Base64.run { bytes.toBase64Url() }
        val decoded = Base64.fromBase64Url(encoded)
        assertContentEquals(bytes.asUnsignedInts(), decoded.asUnsignedInts())
    }

    private fun ByteArray.asUnsignedInts(): List<Int> = map { it.toInt() and 0xFF }
}
