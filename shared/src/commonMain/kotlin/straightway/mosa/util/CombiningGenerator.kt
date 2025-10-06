package straightway.mosa.util

class CombiningGenerator<T>(
    val parts: List<Generator<T>>,
) : BaseGenerator<List<T>>() {

    override val size: Long get() = parts.fold(1L) { acc, gen ->
        acc * gen.size
    }

    override fun get(index: Long): List<T> {
        var restIndex = index
        val generatedParts = parts.map { part ->
            val partSize = part.size
            part[restIndex % partSize].also { restIndex /= partSize }
        }
        if (0L != restIndex) throw IndexOutOfBoundsException()
        return generatedParts
    }
}