package straightway.mosa.util

import kotlin.experimental.and

class BitStream(
    private val bytes: ByteArray,
) {
    private var byteIndex = 0
    private var bitIndex = 0

    val consumedBits: Int get() = bitIndex + byteIndex * 8

    fun getNextAsInt(numBits: Int): Int = (1..numBits).fold(0) { acc, _ ->
        (acc shl 1) + getNextBit()
    }

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
