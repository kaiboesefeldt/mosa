package straightway.mosa.util

class CombiningTextGenerator(
    val parts: List<TextGenerator>,
    val joiner: (partOptions: List<String>) -> String = { partOptions ->
        partOptions.joinToString("")
    }
) : TextGenerator {

    override val numberOfPossibleOutputs: Int get() = parts.fold(1) { acc, gen ->
        acc * gen.numberOfPossibleOutputs
    }

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