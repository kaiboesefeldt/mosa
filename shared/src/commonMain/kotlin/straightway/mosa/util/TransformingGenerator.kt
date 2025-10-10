package straightway.mosa.util

class TransformingGenerator<I, O>(
    private val transformed: Generator<I>,
    private val transformer: (I) -> O,
) : BaseGenerator<O>() {
    override val size: Long get() = transformed.size

    override fun get(index: Long): O = transformer(transformed[index])
}
