package straightway.mosa.util

class FakeTextGenerator(
    override val numberOfPossibleOutputs: Int = 0,
    val generator: (BitStream) -> String = { "" }
) : TextGenerator {
    override fun generate(bits: BitStream): String = generator(bits)
}