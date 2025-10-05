package straightway.mosa.util

class RepeatingTextGenerator(
    val toRepeat: TextGenerator,
    val repetitions: IntRange,
    val joiner: (partOptions: List<String>) -> String = { partOptions ->
        partOptions.joinToString("")
    }
) : TextGenerator {

    private val repetitionRangeSize = repetitions.last - repetitions.first

    override val numberOfPossibleOutputs: Long get() =
        repetitions.fold(0L) { acc, it -> acc + it * toRepeat.numberOfPossibleOutputs }

    override val bitConsumption: IntRange get() =
        bitsRequiredForMax(repetitionRangeSize).let { rangeBits ->
            rangeBits + repetitions.first * toRepeat.bitConsumption.first..
                    rangeBits + repetitions.last * toRepeat.bitConsumption.last
        }

    override val allPossibleOutputs: Sequence<String> get() = sequence {
        repetitions.forEach { reps ->
            combinationsOf(reps).forEach { yield(joiner(it))}
        }
    }

    override fun generate(bits: BitStream) = bits.checkConsumption(bitConsumption, this::class.simpleName) {
        val repetitions = bits.getMax(repetitionRangeSize) + this@RepeatingTextGenerator.repetitions.first
        val parts = (1..repetitions).map { toRepeat.generate(bits) }
        joiner(parts)
    }

    fun joinBy(joiner: (partOptions: List<String>) -> String) = RepeatingTextGenerator(toRepeat, repetitions, joiner)

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