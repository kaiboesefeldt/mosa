package straightway.mosa.util

class CombiningGenerator<T>(
    val parts: List<Generator<T>>,
) : BaseGenerator<List<T>>() {
    override val size: Long
        get() =
            parts.fold(1L) { accumulator, generator ->
                accumulator * generator.size
            }

    override fun get(index: Long): List<T> {
        if (size <= index) throw IndexOutOfBoundsException()
        var remainingIndex = index
        val generatedParts =
            parts.map { part ->
                val partSize = part.size
                part[remainingIndex % partSize].also { remainingIndex /= partSize }
            }
        return generatedParts
    }
}
