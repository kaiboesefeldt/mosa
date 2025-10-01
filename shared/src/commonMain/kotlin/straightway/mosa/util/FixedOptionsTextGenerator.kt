package straightway.mosa.util

class FixedOptionsTextGenerator(
    val textOptions: List<String>
) : TextGenerator {

    override val numberOfPossibleOutputs: Int = textOptions.size

    override val allPossibleOutputs: Sequence<String> get() = textOptions.asSequence()

    override fun generate(bits: BitStream): String {
        return textOptions[bits.getMax(textOptions.lastIndex)]
    }

    companion object {
        operator fun invoke(vararg names: String) = FixedOptionsTextGenerator(names.toList())
    }
}