package straightway.mosa.util

class SelectingGenerator<T>(
    val options: List<Generator<T>>,
) : BaseGenerator<T>() {

    override val size: Long get() = options.sumOf { it.size }
    override operator fun get(index: Long): T {
        var restIndex = index
        for (option in options.map { it}) {
            val optionSize = option.size
            if (restIndex < optionSize) return option[restIndex]
            restIndex -= optionSize
        }

        throw IndexOutOfBoundsException()
    }
}