package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BitStreamTest {
    @Test
    fun `reads least significant bits first`() {
        val stream = BitStream(byteArrayOf(0xA3.toByte()))

        val bits = List(8) { stream.getNextBit() }

        assertEquals(listOf(1, 1, 0, 0, 0, 1, 0, 1), bits)
    }

    @Test
    fun `reads across byte boundaries`() {
        val stream = BitStream(byteArrayOf(0xFF.toByte(), 0x01))

        val bits = List(9) { stream.getNextBit() }

        assertEquals(List(8) { 1 }, bits.take(8))
        assertEquals(1, bits[8])
    }

    @Test
    fun `aggregates bits into an Int`() {
        val stream = BitStream(byteArrayOf(0x1E))

        val value = stream.getNextAsInt(4)

        assertEquals(7, value)
    }

    @Test
    fun `returns zero after exhausting bytes`() {
        val stream = BitStream(byteArrayOf(0xFF.toByte()))

        stream.getNextAsInt(8)

        val trailingBits = stream.getNextAsInt(3)

        assertEquals(0, trailingBits)
    }
}
