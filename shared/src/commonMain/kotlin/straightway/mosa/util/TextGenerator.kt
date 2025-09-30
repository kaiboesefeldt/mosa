package straightway.mosa.util

interface TextGenerator {
    val numberOfPossibleOutputs: Int
    fun generate(bits: BitStream): String
}
