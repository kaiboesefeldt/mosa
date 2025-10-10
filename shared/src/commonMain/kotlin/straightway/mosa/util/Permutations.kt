package straightway.mosa.util

fun <T> allPermsOf(firstPerm: List<T>): Sequence<List<T>> =
    sequence {
        if (firstPerm.isEmpty()) return@sequence
        if (firstPerm.size == 1) {
            yield(firstPerm)
            return@sequence
        }

        val lowerPerms = allPermsOf(firstPerm.drop(1))
        val firstItem = firstPerm.first()
        lowerPerms.forEach { lowerPerm ->
            (0..lowerPerm.size).forEach {
                yield(lowerPerm.drop(it) + firstItem + lowerPerm.take(it))
            }
        }
    }
