package straightway.mosa.util

import kotlin.experimental.and
import kotlin.random.Random

class BitStream(private val bytes: ByteArray) : Random() {
    private var byteIndex = 0
    private var bitIndex = 0

    val consumedBits: Int get() = bitIndex + byteIndex * 8

    override fun nextBits(bitCount: Int): Int = (1..bitCount).fold(0) { acc, _ ->
        (acc shl 1) + nextBit()
    }

    fun nextBitsLong(bitCount: Int): Long = (1..bitCount).fold(0L) { acc, _ ->
        (acc shl 1) + nextBit()
    }

    override fun nextInt(until: Int): Int {
        val max = until - 1
        if (max < 0) throw RuntimeException("negative numbers not supported (got: ${max})")
        val bitsRequired = bitsRequiredForMax(max)
        return nextBits(bitsRequired) % until
    }

    override fun nextInt(from: Int, until: Int): Int = from + nextInt(until - from)

    override fun nextLong(until: Long): Long {
        val max = until - 1L
        if (max < 0L) throw RuntimeException("negative numbers not supported (got: ${max})")
        val bitsRequired = bitsRequiredForMax(max)
        return nextBitsLong(bitsRequired) % until
    }

    override fun nextLong(from: Long, until: Long): Long = from + nextLong(until - from)

    fun nextBit(): Int {
        val currByte = getCurrentByte()
        return currByte.getBit(bitIndex).also {
            ++bitIndex
            if (8 <= bitIndex) jumpToNextByte()
        }
    }

    fun jumpToNextByte() {
        bitIndex = 0
        ++byteIndex
    }

    private fun getCurrentByte(): Byte = bytes.getOrNull(byteIndex) ?: ZERO_BYTE

    private companion object {
        fun Byte.getBit(bitIndex: Int): Int {
            val mask = ((1 shl bitIndex) and 0xFF).toByte()
            return if ((this and mask) == ZERO_BYTE) 0 else 1
        }

        private const val ZERO_BYTE = 0.toByte()
    }
}

fun bitsRequiredForMax(max: Int): Int = Int.SIZE_BITS - max.countLeadingZeroBits()
fun bitsRequiredForMax(max: Long): Int = Long.SIZE_BITS - max.countLeadingZeroBits()
