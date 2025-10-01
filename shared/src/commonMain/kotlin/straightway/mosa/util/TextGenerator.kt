package straightway.mosa.util

interface TextGenerator {
    val numberOfPossibleOutputs: Long
    val allPossibleOutputs: Sequence<String> get() = emptySequence()
    fun generate(bits: BitStream): String
}
