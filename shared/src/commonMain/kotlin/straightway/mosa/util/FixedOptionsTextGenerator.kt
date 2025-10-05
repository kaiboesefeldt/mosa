package straightway.mosa.util

class FixedOptionsTextGenerator(
    val textOptions: List<String>
) : TextGenerator.Base() {

    override val size: Long = textOptions.size.toLong()
    override operator fun get(index: Long): String = textOptions[index.toInt()]
}