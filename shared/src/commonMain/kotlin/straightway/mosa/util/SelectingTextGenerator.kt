package straightway.mosa.util

class SelectingTextGenerator(
    val options: List<Pair<Int, TextGenerator>>,
) : TextGenerator {

    private val weightSums = options.sumOf { it.first }

    override val numberOfPossibleOutputs: Long get() = options.sumOf { it.second.numberOfPossibleOutputs }

    override val bitConsumption: IntRange get() =
        bitsRequiredForMax(weightSums).let { weightBits ->
            (weightBits + options.minOf { it.second.bitConsumption.first })..
                    (weightBits + options.maxOf { it.second.bitConsumption.last })
        }

    override val allPossibleOutputs: Sequence<String> get() = sequence {
        options.forEach { option ->
            option.second.allPossibleOutputs.forEach { yield(it) }
        }
    }

    override fun generate(bits: BitStream) = bits.checkConsumption(bitConsumption, this::class.simpleName) {
        val selector = bits.getMax(weightSums)
        var currSum = options.firstOrNull()?.first ?: 0
        var currIndex = 0
        while (currSum < selector && currIndex < options.lastIndex) {
            currSum += options[currIndex].first
            ++currIndex
        }
        options[currIndex].second.generate(bits)
    }

    companion object {
        operator fun invoke(vararg options: Pair<Int, TextGenerator>) =
            SelectingTextGenerator(options.toList())
        operator fun invoke(vararg options: TextGenerator) =
            SelectingTextGenerator(options.map { it.numberOfPossibleOutputs.toInt() to it })
    }
}