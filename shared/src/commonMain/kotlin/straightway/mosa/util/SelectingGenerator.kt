package straightway.mosa.util

class SelectingGenerator<T>(
    val options: List<Generator<T>>,
) : BaseGenerator<T>() {
    override val size: Long
        get() = options.sumOf { option -> option.size }

    override operator fun get(index: Long): T {
        var remainingIndex = index
        for (option in options) {
            val optionSize = option.size
            if (remainingIndex < optionSize) return option[remainingIndex]
            remainingIndex -= optionSize
        }
        throw IndexOutOfBoundsException()
    }
}
