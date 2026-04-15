package straightway.mosa.structuredstate

interface StructuredStateContext {
    fun <T> execute(block: () -> T): T
}
