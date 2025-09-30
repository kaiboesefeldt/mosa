package straightway.mosa.util

fun interface TextGenerator {
    fun generate(bits: BitStream): String
}
