package straightway.mosa.util

fun flat(gen: TextGenerator) = FixedOptionsTextGenerator(gen.toList())

operator fun TextGenerator.plus(other: TextGenerator) =
    (if (other is CombiningTextGenerator) other.parts else listOf(other)).let { otherParts ->
        when (this) {
            is CombiningTextGenerator -> CombiningTextGenerator(parts + otherParts)
            else -> CombiningTextGenerator(listOf(this) + otherParts)
        }
    }

operator fun TextGenerator.plus(other: String) = this + anyOf(other)

operator fun TextGenerator.div(other: TextGenerator) =
    (if (other is SelectingTextGenerator) other.options else listOf(other)).let { otherOptions ->
        when (this) {
            is SelectingTextGenerator -> SelectingTextGenerator(options + otherOptions)
            else -> SelectingTextGenerator(listOf(this) + otherOptions)
        }
    }

operator fun TextGenerator.div(other: String) = this / anyOf(other)

operator fun IntRange.times(toRepeat: TextGenerator) =
    RepeatingTextGenerator(toRepeat, this)

operator fun Int.times(toRepeat: TextGenerator) = (this..this) * toRepeat

fun combine(vararg gen: TextGenerator) = SelectingTextGenerator(
    allPermsOf(gen.toList()).map { perm ->
        CombiningTextGenerator(perm)
    }.map { it }.toList()
)

fun anyOf(vararg s: String) = FixedOptionsTextGenerator(s.toList())