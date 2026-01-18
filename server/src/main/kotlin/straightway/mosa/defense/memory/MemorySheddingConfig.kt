package straightway.mosa.defense.memory

data class MemorySheddingConfig(
    val memorySheddingStart: Double,
    val memorySheddingFull: Double,
) {
    init {
        require(0.0 <= memorySheddingStart) { "memorySheddingStart must be >= 0.0" }
        require(memorySheddingStart <= memorySheddingFull) { "memorySheddingStart must be <= memorySheddingFull" }
    }
}
