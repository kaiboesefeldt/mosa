package straightway.mosa.util

class FixedOptionsTextGenerator(
    val textOptions: List<String>
) : TextGenerator {

    override val numberOfPossibleOutputs: Long = textOptions.size.toLong()
    override val bitConsumption: IntRange = bitsRequiredForMax(textOptions.size).let { it .. it}
    override val allPossibleOutputs: Sequence<String> get() = textOptions.asSequence()

    override fun generate(bits: BitStream) = bits.checkConsumption(bitConsumption, this::class.simpleName) {
        textOptions[bits.getMax(textOptions.lastIndex)]
    }

    companion object {
        operator fun invoke(vararg names: String) = FixedOptionsTextGenerator(names.toList())
    }
}