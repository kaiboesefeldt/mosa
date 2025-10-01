package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun `consumedBits initially returns 0 consumed bits`() {
        val stream = BitStream(byteArrayOf(0xFF.toByte()))
        assertEquals(0, stream.consumedBits)
    }

    @Test
    fun `consumedBits returns first consumed bit`() {
        val stream = BitStream(byteArrayOf(0xFF.toByte()))
        stream.getNextBit()
        assertEquals(1, stream.consumedBits)
    }

    @Test
    fun `consumedBits returns number of consumed bits when crossing the byte border`() {
        val stream = BitStream(byteArrayOf(0xFF.toByte()))
        repeat(9) { stream.getNextBit() }
        assertEquals(9, stream.consumedBits)
    }

    @Test
    fun `bitsRequiredForMax 0 returns 0`() =
        assertEquals(0, bitsRequiredForMax(0))

    @Test
    fun `bitsRequiredForMax 1 returns 1`() =
        assertEquals(1, bitsRequiredForMax(1))

    @Test
    fun `bitsRequiredForMax 2 returns 2`() =
        assertEquals(2, bitsRequiredForMax(2))

    @Test
    fun `bitsRequiredForMax 3 returns 2`() =
        assertEquals(2, bitsRequiredForMax(3))

    @Test
    fun `bitsRequiredForMax 4 returns 3`() =
        assertEquals(3, bitsRequiredForMax(4))

    @Test
    fun `bitsRequiredForMax 5 returns 3`() =
        assertEquals(3, bitsRequiredForMax(5))

    @Test
    fun `bitsRequiredForMax -1 returns 32`() =
        assertEquals(32, bitsRequiredForMax(-1))

    @Test
    fun `getMax for negative numbers throws`() {
        assertFailsWith(RuntimeException::class) {
            BitStream(byteArrayOf()).getMax(-1)
        }
    }

    @Test
    fun `getMax for 0 consumes no bits and returns 0`() {
        val bitStream = BitStream(byteArrayOf(0xFF.toByte()))
        assertEquals(0, bitStream.getMax(0))
        assertEquals(0, bitStream.consumedBits)
    }

    @Test
    fun `getMax for 1 consumes 1 bit and returns 0 for stream starting with 0`() {
        val bitStream = BitStream(byteArrayOf(0xFE.toByte()))
        assertEquals(0, bitStream.getMax(1))
        assertEquals(1, bitStream.consumedBits)
    }

    @Test
    fun `getMax for 1 consumes 1 bit and returns 1 for stream starting with 1`() {
        val bitStream = BitStream(byteArrayOf(0x1.toByte()))
        assertEquals(1, bitStream.getMax(1))
        assertEquals(1, bitStream.consumedBits)
    }

    @Test
    fun `getMax for 2 consumes 2 bits and returns 0 for stream 0 0`() {
        val bitStream = BitStream(byteArrayOf(0x0.toByte()))
        assertEquals(0, bitStream.getMax(2))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `getMax for 2 consumes 2 bits and returns 1 for stream 1 0`() {
        val bitStream = BitStream(byteArrayOf(0x2.toByte()))
        assertEquals(1, bitStream.getMax(2))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `getMax for 2 consumes 2 bits and returns 2 for stream 0 1`() {
        val bitStream = BitStream(byteArrayOf(0x1.toByte()))
        assertEquals(2, bitStream.getMax(2))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `getMax for 2 consumes 2 bits and returns 1 for stream 0 1 1`() {
        val bitStream = BitStream(byteArrayOf(0x3.toByte()))
        assertEquals(0, bitStream.getMax(2))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `getMax for 5 consumes 3 bits and returns 4 for stream 0 0 1`() {
        val bitStream = BitStream(byteArrayOf(0x1.toByte()))
        assertEquals(4, bitStream.getMax(5))
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `getMax for 5 consumes 3 bits and returns 5 for stream 1 0 1`() {
        val bitStream = BitStream(byteArrayOf(0x5.toByte()))
        assertEquals(5, bitStream.getMax(5))
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `getMax for 4 consumes 3 bits and returns 1 for stream 0 1 1`() {
        val bitStream = BitStream(byteArrayOf(0x3.toByte()))
        assertEquals(1, bitStream.getMax(4))
        assertEquals(3, bitStream.consumedBits)
    }
}
