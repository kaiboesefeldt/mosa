package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BitStreamTest {
    @Test
    fun `nextBit reads least significant bits first`() {
        val stream = BitStream(byteArrayOf(0b10100011.toByte()))
        val bits = List(8) { stream.nextBit() }
        assertEquals(listOf(1, 1, 0, 0, 0, 1, 0, 1), bits)
    }

    @Test
    fun `nextBit reads across byte boundaries`() {
        val stream = BitStream(byteArrayOf(0b11111111.toByte(), 0b01))
        val bits = List(9) { stream.nextBit() }
        assertEquals(List(8) { 1 }, bits.take(8))
        assertEquals(1, bits[8])
    }

    @Test
    fun `aggregates bits into an Int`() {
        val stream = BitStream(byteArrayOf(0b00011110))
        val value = stream.nextBits(4)
        assertEquals(7, value)
    }

    @Test
    fun `returns zero after exhausting bytes`() {
        val stream = BitStream(byteArrayOf(0b11111111.toByte()))
        stream.nextBits(8)
        val trailingBits = stream.nextBits(3)
        assertEquals(0, trailingBits)
    }

    @Test
    fun `consumedBits initially returns 0 consumed bits`() {
        val stream = BitStream(byteArrayOf(0b11111111.toByte()))
        assertEquals(0, stream.consumedBits)
    }

    @Test
    fun `consumedBits returns first consumed bit`() {
        val stream = BitStream(byteArrayOf(0b11111111.toByte()))
        stream.nextBit()
        assertEquals(1, stream.consumedBits)
    }

    @Test
    fun `consumedBits returns number of consumed bits when crossing the byte border`() {
        val stream = BitStream(byteArrayOf(0b11111111.toByte()))
        repeat(9) { stream.nextBit() }
        assertEquals(9, stream.consumedBits)
    }

    @Test
    fun `bitsRequiredForMax 0 returns 0`() = assertEquals(0, bitsRequiredForMax(0))

    @Test
    fun `bitsRequiredForMax 1 returns 1`() = assertEquals(1, bitsRequiredForMax(1))

    @Test
    fun `bitsRequiredForMax 2 returns 2`() = assertEquals(2, bitsRequiredForMax(2))

    @Test
    fun `bitsRequiredForMax 3 returns 2`() = assertEquals(2, bitsRequiredForMax(3))

    @Test
    fun `bitsRequiredForMax 4 returns 3`() = assertEquals(3, bitsRequiredForMax(4))

    @Test
    fun `bitsRequiredForMax 5 returns 3`() = assertEquals(3, bitsRequiredForMax(5))

    @Test
    fun `bitsRequiredForMax -1 returns 32`() = assertEquals(32, bitsRequiredForMax(-1))

    @Test
    fun `bitsRequiredForMax -1L returns 32`() = assertEquals(64, bitsRequiredForMax(-1L))

    @Test
    fun `nextLong for negative zero throws`() {
        assertFailsWith(RuntimeException::class) {
            BitStream(byteArrayOf()).nextLong(0)
        }
    }

    @Test
    fun `nextLong for 1 consumes no bits and returns 0`() {
        val bitStream = BitStream(byteArrayOf(0b11111111.toByte()))
        assertEquals(0, bitStream.nextLong(1))
        assertEquals(0, bitStream.consumedBits)
    }

    @Test
    fun `nextLong for 2 consumes 1 bit and returns 0 for stream starting with 0`() {
        val bitStream = BitStream(byteArrayOf(0b11111110.toByte()))
        assertEquals(0, bitStream.nextLong(2))
        assertEquals(1, bitStream.consumedBits)
    }

    @Test
    fun `nextLong for 2 consumes 1 bit and returns 1 for stream starting with 1`() {
        val bitStream = BitStream(byteArrayOf(0b01.toByte()))
        assertEquals(1, bitStream.nextLong(2))
        assertEquals(1, bitStream.consumedBits)
    }

    @Test
    fun `nextLong for 4 consumes 2 bits and returns 0 for stream 0 0`() {
        val bitStream = BitStream(byteArrayOf(0b00.toByte()))
        assertEquals(0, bitStream.nextLong(4))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `nextLong for 3 consumes 2 bits and returns 1 for stream 1 0`() {
        val bitStream = BitStream(byteArrayOf(0b10.toByte()))
        assertEquals(1, bitStream.nextLong(3))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `nextLong for 4 consumes 2 bits and returns 2 for stream 0 1`() {
        val bitStream = BitStream(byteArrayOf(0b01.toByte()))
        assertEquals(2, bitStream.nextLong(4))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `nextLong for 3 consumes 2 bits and returns 1 for stream 1 1`() {
        val bitStream = BitStream(byteArrayOf(0b11.toByte()))
        assertEquals(0, bitStream.nextLong(3))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `nextLong for 5 consumes 3 bits and returns 4 for stream 0 0 1`() {
        val bitStream = BitStream(byteArrayOf(0b001.toByte()))
        assertEquals(4, bitStream.nextLong(6))
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `nextLong for 5 consumes 3 bits and returns 5 for stream 1 0 1`() {
        val bitStream = BitStream(byteArrayOf(0b101.toByte()))
        assertEquals(5, bitStream.nextLong(6))
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `nextLong for 4 consumes 3 bits and returns 1 for stream 0 1 1`() {
        val bitStream = BitStream(byteArrayOf(0b011.toByte()))
        assertEquals(1, bitStream.nextLong(5))
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `nextLong with range`() {
        val bitStream = BitStream(byteArrayOf(0b011.toByte()))
        assertEquals(1001, bitStream.nextLong(1000, 1005))
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for negative zero throws`() {
        assertFailsWith(RuntimeException::class) {
            BitStream(byteArrayOf()).nextInt(0)
        }
    }

    @Test
    fun `nextInt for 1 consumes no bits and returns 0`() {
        val bitStream = BitStream(byteArrayOf(0b11111111.toByte()))
        assertEquals(0, bitStream.nextInt(1))
        assertEquals(0, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for 2 consumes 1 bit and returns 0 for stream starting with 0`() {
        val bitStream = BitStream(byteArrayOf(0b11111110.toByte()))
        assertEquals(0, bitStream.nextInt(2))
        assertEquals(1, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for 2 consumes 1 bit and returns 1 for stream starting with 1`() {
        val bitStream = BitStream(byteArrayOf(0b01.toByte()))
        assertEquals(1, bitStream.nextInt(2))
        assertEquals(1, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for 4 consumes 2 bits and returns 0 for stream 0 0`() {
        val bitStream = BitStream(byteArrayOf(0b00.toByte()))
        assertEquals(0, bitStream.nextInt(4))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for 3 consumes 2 bits and returns 1 for stream 1 0`() {
        val bitStream = BitStream(byteArrayOf(0b10.toByte()))
        assertEquals(1, bitStream.nextInt(3))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for 4 consumes 2 bits and returns 2 for stream 0 1`() {
        val bitStream = BitStream(byteArrayOf(0b01.toByte()))
        assertEquals(2, bitStream.nextInt(4))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for 3 consumes 2 bits and returns 1 for stream 1 1`() {
        val bitStream = BitStream(byteArrayOf(0b11.toByte()))
        assertEquals(0, bitStream.nextInt(3))
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for 5 consumes 3 bits and returns 4 for stream 0 0 1`() {
        val bitStream = BitStream(byteArrayOf(0b001.toByte()))
        assertEquals(4, bitStream.nextInt(6))
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for 5 consumes 3 bits and returns 5 for stream 1 0 1`() {
        val bitStream = BitStream(byteArrayOf(0b101.toByte()))
        assertEquals(5, bitStream.nextInt(6))
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `nextInt for 4 consumes 3 bits and returns 1 for stream 0 1 1`() {
        val bitStream = BitStream(byteArrayOf(0b011.toByte()))
        assertEquals(1, bitStream.nextInt(5))
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `nextInt with range`() {
        val bitStream = BitStream(byteArrayOf(0b011.toByte()))
        assertEquals(1001, bitStream.nextInt(1000, 1005))
        assertEquals(3, bitStream.consumedBits)
    }
}
