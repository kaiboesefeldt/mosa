package straightway.mosa.util

class CombiningTextGenerator(
    val parts: List<TextGenerator>,
    val joiner: (partOptions: List<String>) -> String = { partOptions ->
        partOptions.joinToString("")
    }
) : TextGenerator.Base() {

    override val size: Long get() = parts.fold(1L) { acc, gen ->
        acc * gen.size
    }

    override fun get(index: Long): String {
        var restIndex = index
        val partStrings = parts.map { part ->
            val partSize = part.size
            part[restIndex % partSize].also { restIndex /= partSize }
        }
        if (0L != restIndex) throw IndexOutOfBoundsException()
        return joiner(partStrings)
    }

    fun joinBy(joiner: (partOptions: List<String>) -> String) = CombiningTextGenerator(parts, joiner)
}