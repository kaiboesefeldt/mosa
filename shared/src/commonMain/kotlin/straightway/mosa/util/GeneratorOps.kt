package straightway.mosa.util

fun <T> flat(gen: Generator<T>) = FixedOptionsGenerator(gen.toList())
fun <T> anyOf(vararg s: T) = FixedOptionsGenerator(s.toList())
fun <T> anyOf(vararg g: Generator<T>) = SelectingGenerator(g.toList())

fun <I, O> concat(vararg gens: Generator<I>, joiner: (List<I>) -> O): Generator<O> =
    CombiningGenerator(gens.toList()).trans(joiner)

fun <T> concat(vararg gens: Generator<T>): Generator<List<T>> = concat(*gens) { it }

operator fun <T> IntRange.times(toRepeat: Generator<T>) = RepeatingGenerator(toRepeat, this)
operator fun <T> Int.times(toRepeat: Generator<T>) = (this..this) * toRepeat

fun <T> combine(vararg gen: Generator<T>) = SelectingGenerator(
    allPermsOf(gen.toList()).map { perm ->
        CombiningGenerator(perm)
    }.map { it }.toList()
)

fun <I, O> Generator<I>.trans(transformer: (I) -> O) = TransformingGenerator(this, transformer)
fun <T> Generator<List<T>>.joinBy(transformer: (List<T>) -> String) = trans(transformer)
fun <T> stringGen(
    gen: Generator<List<T>>,
    separator: CharSequence = "",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null
) = gen.joinBy { it.joinToString(separator, prefix, postfix, limit, truncated, transform) }
operator fun Generator<List<String>>.unaryPlus() = stringGen(this)