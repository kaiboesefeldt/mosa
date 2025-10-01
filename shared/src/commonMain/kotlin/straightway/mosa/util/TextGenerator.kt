package straightway.mosa.util

interface TextGenerator {
    val numberOfPossibleOutputs: Long
    val allPossibleOutputs: Sequence<String>
    val bitConsumption: IntRange
    fun generate(bits: BitStream): String
}
