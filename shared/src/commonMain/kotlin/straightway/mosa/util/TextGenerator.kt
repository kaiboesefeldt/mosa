package straightway.mosa.util

fun interface TextGenerator {
    fun generate(bits: BitStream): String
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
        ) = CombiningTextGenerator(parts.toList(), joiner)
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
        val selector = bits.getMax(weightSums)
        var currSum = options.firstOrNull()?.first ?: 0
        var currIndex = 0
        while (currSum < selector && currIndex < options.lastIndex) {
            currSum += options[currIndex].first
            ++currIndex
        }
        return options[currIndex].second.generate(bits)
    }

    companion object {
        operator fun invoke(vararg options: Pair<Int, TextGenerator>) =
            SelectingTextGenerator(options.toList())
    }
}
