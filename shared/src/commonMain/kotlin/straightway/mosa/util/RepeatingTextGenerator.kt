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

    override val allPossibleOutputs: Sequence<String> get() = sequence {
        repetitions.forEach { reps ->
            combinationsOf(reps).forEach { yield(joiner(it))}
        }
    }

    override fun generate(bits: BitStream): String {
        val repetitionRangeSize = this@RepeatingTextGenerator.repetitions.last - this@RepeatingTextGenerator.repetitions.first
        val repetitions = bits.getMax(repetitionRangeSize) + this@RepeatingTextGenerator.repetitions.first
        val parts = (1..repetitions).map { toRepeat.generate(bits) }
        return joiner(parts)
    }

    private fun combinationsOf(reps: Int): Sequence<List<String>> =
        if (reps < 1) emptySequence()
        else sequence {
            toRepeat.allPossibleOutputs.forEach { firstRepOutput ->
                val nextLowerCombinationsOfReps = combinationsOf(reps - 1)
                if (nextLowerCombinationsOfReps.any()) {
                    nextLowerCombinationsOfReps.forEach { restReps ->
                        yield(listOf(firstRepOutput) + restReps)
                    }
                } else yield(listOf(firstRepOutput))
            }
        }
}