package straightway.mosa.util

class FakeTextGenerator(
    override val numberOfPossibleOutputs: Long = 0L,
    override val allPossibleOutputs: Sequence<String> = emptySequence(),
    val generator: (BitStream) -> String = { "" }
) : TextGenerator {
    override fun generate(bits: BitStream): String = generator(bits)
}