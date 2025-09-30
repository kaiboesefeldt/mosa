package straightway.mosa.util

class RepeatingTextGenerator(
    val toRepeat: TextGenerator,
    val repetitions: IntRange,
    val joiner: (partOptions: List<String>) -> String = { partOptions ->
        partOptions.joinToString("")
    }
) : TextGenerator {

    override val numberOfPossibleOutputs: Int
        get() = repetitions.fold(0) { acc, it -> acc + it * toRepeat.numberOfPossibleOutputs }

    override fun generate(bits: BitStream): String {
        val repetitionRangeSize = this@RepeatingTextGenerator.repetitions.last - this@RepeatingTextGenerator.repetitions.first
        val repetitions = bits.getMax(repetitionRangeSize) + this@RepeatingTextGenerator.repetitions.first
        val parts = (1..repetitions).map { toRepeat.generate(bits) }
        return joiner(parts)
    }
}