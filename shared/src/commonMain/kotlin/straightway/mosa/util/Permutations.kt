package straightway.mosa.util

private class PermutedList<T>(
    private val base: List<T>,
    private val idx: IntArray,
) : AbstractList<T>() {
    override val size: Int get() = idx.size

    override fun get(index: Int): T = base[idx[index]]
}

private fun IntArray.swap(
    i: Int,
    j: Int,
) {
    if (i == j) return
    val t = this[i]
    this[i] = this[j]
    this[j] = t
}

fun <T> allPermsOf(firstPerm: List<T>): Sequence<List<T>> =
    sequence {
        val n = firstPerm.size
        if (n == 0) return@sequence

        yield(firstPerm)

        if (n == 1) return@sequence

        val idx = IntArray(n) { it }
        val counters = IntArray(n)

        var i = 0
        while (i < n) {
            if (counters[i] < i) {
                val j = if (i % 2 == 0) 0 else counters[i]
                idx.swap(i, j)

                yield(PermutedList(firstPerm, idx.copyOf()))

                counters[i]++
                i = 0
            } else {
                counters[i] = 0
                i++
            }
        }
    }
