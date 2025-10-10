package straightway.mosa.util

interface Generator<T> : Iterable<T> {
    val size: Long

    operator fun get(index: Long): T
}

abstract class BaseGenerator<T> : Generator<T> {
    override fun iterator(): Iterator<T> =
        sequence {
            (0..<size).forEach { yield(get(it)) }
        }.iterator()
}
