package straightway.mosa.content

interface StructuredStateContext {
    fun <T> execute(block: () -> T): T
}
