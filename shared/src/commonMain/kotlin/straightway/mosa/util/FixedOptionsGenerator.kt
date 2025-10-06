package straightway.mosa.util

class FixedOptionsGenerator<T>(
    val textOptions: List<T>
) : BaseGenerator<T>() {

    override val size: Long = textOptions.size.toLong()
    override operator fun get(index: Long): T = textOptions[index.toInt()]
}