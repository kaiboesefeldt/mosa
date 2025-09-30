package straightway.mosa.util

class RepeatingTextGenerator(
    val toRepeat: TextGenerator,
    val repetitions: (BitStream) -> Int,
    val joiner: (partOptions: List<String>) -> String = { partOptions ->
        partOptions.joinToString("")
    }
) : TextGenerator {
    override fun generate(bits: BitStream): String {
        val repititions = repetitions(bits)
        val parts = (1..repititions).map { toRepeat.generate(bits) }
        return joiner(parts)
    }
}