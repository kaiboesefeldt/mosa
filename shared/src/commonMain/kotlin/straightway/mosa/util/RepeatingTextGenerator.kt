package straightway.mosa.util

class RepeatingTextGenerator(
    val toRepeat: TextGenerator,
    val repetitions: IntRange,
    val joiner: (partOptions: List<String>) -> String = { partOptions ->
        partOptions.joinToString("")
    }
) : TextGenerator.Base() {

    override val size: Long get() =
        repetitions.fold(0L) { acc, it -> acc + numberOfOptionForRepeats(it) }

    override fun get(index: Long): String {
        val toRepeatSize = toRepeat.size
        var restIndex = index
        for (currRepetitions in repetitions) {
            val numberOfOptionsForCurrRepetition = numberOfOptionForRepeats(currRepetitions)
            if (restIndex < numberOfOptionsForCurrRepetition) {
                val gen = (1..currRepetitions).map {
                    toRepeat[restIndex % toRepeatSize].also { restIndex /= toRepeatSize }
                }
                return joiner(gen)
            }
            restIndex -= numberOfOptionsForCurrRepetition
        }

        throw IndexOutOfBoundsException()
    }

    fun joinBy(joiner: (partOptions: List<String>) -> String) = RepeatingTextGenerator(toRepeat, repetitions, joiner)

    private fun numberOfOptionForRepeats(numberOfRepetition: Int): Long =
        if (numberOfRepetition == 0) 0
        else (1..numberOfRepetition).fold(1L) { acc, _ -> acc * toRepeat.size }
}