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

    override val allPossibleOutputs: Sequence<String> get() = sequence {
        combinePartOutputs(parts).forEach { yield(joiner(it)) }
    }

    override fun generate(bits: BitStream): String {
        val partStrings = parts.map { it.generate(bits) }
        return joiner(partStrings)
    }

    private fun combinePartOutputs(parts: List<TextGenerator>): Sequence<List<String>> =
        if (parts.isEmpty()) emptySequence()
        else sequence {
            val firstPart = parts.first()
            val restParts = parts.drop(1)
            firstPart.allPossibleOutputs.forEach { firstText ->
                val restOutputs = combinePartOutputs(restParts).toList()
                if (restOutputs.isEmpty()) yield(listOf(firstText))
                else restOutputs.forEach { rest -> yield(listOf(firstText) + rest) }
            }
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