package straightway.mosa.client.identity

import straightway.mosa.util.BitStream

fun interface TextGenerator {
    fun generate(bits: BitStream): String
}

fun bitsRequiredForMax(max: Int): Int = if (max == 1) 0 else Int.SIZE_BITS - max.countLeadingZeroBits()
fun BitStream.getMax(max: Int): Int {
    val bitsRequired = bitsRequiredForMax(max)
    val maxBitMask = (1 shl bitsRequired) - 1
    var result = getNextAsInt(bitsRequired)
    while (max < result) {
        result = ((result shl 1) and maxBitMask) + getNextBit()
    }
    return result
}

class FixedOptionsTextGenerator(
    val textOptions: List<String>
) : TextGenerator {
    override fun generate(bits: BitStream): String {
        return textOptions[bits.getMax(textOptions.lastIndex)]
    }

    companion object {
        operator fun invoke(vararg names: String) = FixedOptionsTextGenerator(names.toList())
    }
}

class CombiningTextGenerator(
    val parts: List<TextGenerator>,
    val joiner: (partOptions: List<String>) -> String = { partOptions ->
        partOptions.joinToString("")
    }
) : TextGenerator {
    override fun generate(bits: BitStream): String {
        val partStrings = parts.map { it.generate(bits) }
        return joiner(partStrings)
    }

    companion object {
        operator fun invoke(
            vararg parts: TextGenerator
        ) = CombiningTextGenerator(parts.toList())
        operator fun invoke(
            joiner: (partOptions: List<String>) -> String,
            vararg parts: TextGenerator
        ) = CombiningTextGenerator(parts.toList(), joiner).also { console.log(it) }
    }
}

class RepeatingTextGenerator(
    val toRepeat: TextGenerator,
    val repetitions: (BitStream) -> Int,
    val joiner: (partOptions: List<String>) -> String = { partOptions ->
        partOptions.joinToString("")
    }
) : TextGenerator {
    override fun generate(bits: BitStream): String {
        val repititions = repetitions(bits)
        val parts = (1..repititions).map { toRepeat.generate(bits) }
        return joiner(parts)
    }
}

class SelectingTextGenerator(
    val options: List<Pair<Int, TextGenerator>>,
) : TextGenerator {

    private val weightSums = options.sumOf { it.first }

    override fun generate(bits: BitStream): String {
        console.log("weights: ${options.map { it.first }.joinToString(",") }")
        val selector = bits.getMax(weightSums)
        var currSum = options.firstOrNull()?.first ?: 0
        var currIndex = 0
        console.log("lastIndex = ${options.lastIndex}")
        while (currSum < selector && currIndex < options.lastIndex) {
            console.log(("Skipping choice $currIndex for selector $selector, sum = $currSum"))
            currSum += options[currIndex].first
            ++currIndex
        }
        return options[currIndex].second.generate(bits).also {
            console.log("selector = $selector, generated = $it, index = $currIndex, sum = $currSum")
        }
    }

    companion object {
        operator fun invoke(vararg options: Pair<Int, TextGenerator>) =
            SelectingTextGenerator(options.toList())
    }
}
