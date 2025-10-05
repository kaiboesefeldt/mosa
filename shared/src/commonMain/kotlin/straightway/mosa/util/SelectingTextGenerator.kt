package straightway.mosa.util

class SelectingTextGenerator(
    val options: List<TextGenerator>,
) : TextGenerator.Base() {

    override val size: Long get() = options.sumOf { it.size }
    override operator fun get(index: Long): String {
        var restIndex = index
        for (option in options.map { it}) {
            val optionSize = option.size
            if (restIndex < optionSize) return option[restIndex]
            restIndex -= optionSize
        }

        throw IndexOutOfBoundsException()
    }
}