package straightway.mosa.util

interface TextGenerator : Iterable<String> {
    val size: Long
    operator fun get(index: Long): String
    override fun iterator(): Iterator<String>

    abstract class Base : TextGenerator {
        override fun iterator(): Iterator<String> =
            sequence { (0..<size).forEach { yield(get(it)) } }.iterator()
    }
}
