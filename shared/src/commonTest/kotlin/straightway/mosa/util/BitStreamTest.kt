package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BitStreamTest {
    @Test
    fun readsLeastSignificantBitsFirst() {
        val stream = BitStream(byteArrayOf(0xA3.toByte()))

        val bits = List(8) { stream.getNextBit() }

        assertEquals(listOf(1, 1, 0, 0, 0, 1, 0, 1), bits)
    }

    @Test
    fun readsAcrossByteBoundaries() {
        val stream = BitStream(byteArrayOf(0xFF.toByte(), 0x01))

        val bits = List(9) { stream.getNextBit() }

        assertEquals(List(8) { 1 }, bits.take(8))
        assertEquals(1, bits[8])
    }

    @Test
    fun aggregatesBitsIntoAnInt() {
        val stream = BitStream(byteArrayOf(0x1E))

        val value = stream.getNext(4)

        assertEquals(7, value)
    }

    @Test
    fun returnsZeroAfterExhaustingBytes() {
        val stream = BitStream(byteArrayOf(0xFF.toByte()))

        stream.getNext(8)

        val trailingBits = stream.getNext(3)

        assertEquals(0, trailingBits)
    }
}
