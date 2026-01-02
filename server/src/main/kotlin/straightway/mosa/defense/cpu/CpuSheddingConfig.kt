package straightway.mosa.defense.cpu

data class CpuSheddingConfig(
    val loadSheddingStart: Double,
    val loadSheddingFull: Double,
) {
    init {
        require(0.0 <= loadSheddingStart) { "loadSheddingStart must be >= 0.0" }
        require(loadSheddingFull >= loadSheddingStart) { "loadSheddingFull must be >= loadSheddingStart" }
    }
}
