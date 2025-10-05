package straightway.mosa.util

import kotlin.experimental.and
import kotlin.random.Random

class BitStream(
    private val bytes: ByteArray,
    val checkConsumption: Boolean = false,
) : Random() {
    private var byteIndex = 0
    private var bitIndex = 0

    val consumedBits: Int get() = bitIndex + byteIndex * 8

    override fun nextBits(bitCount: Int): Int = (1..bitCount).fold(0) { acc, _ ->
        (acc shl 1) + getNextBit()
    }

    override fun nextInt(until: Int): Int {
        val max = until - 1
        if (max < 0) throw RuntimeException("negative numbers not supported (got: ${max})")
        val bitsRequired = bitsRequiredForMax(max)
        return nextBits(bitsRequired) % until
    }

    override fun nextInt(from: Int, until: Int): Int = from + nextInt(until - from)

    fun getNextBit(): Int {
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

fun BitStream.getMax(max: Int): Int = nextInt(max + 1)

fun <T> BitStream.checkConsumption(
    validRange: IntRange,
    hint: String? = null,
    block: () -> T
): T =
    if (checkConsumption) {
        val consumptionBefore = consumedBits
        val result = block()
        val consumption = consumedBits - consumptionBefore
        if (consumption !in validRange)
            throw RuntimeException(
                "Bit consumption out of range (result=$result). Expected: $validRange, " +
                        "got: $consumption${hint?.let {", Hint: $it"} ?: ""}"
            )
        result
    } else block()