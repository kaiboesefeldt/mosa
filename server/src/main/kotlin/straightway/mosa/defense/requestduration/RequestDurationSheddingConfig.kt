package straightway.mosa.defense.requestduration

data class RequestDurationSheddingConfig(
    val minSamplesForBaseline: Long = 200,
    val minBaselineMs: Double = 1.0,
    val ratioSlack: Double = 0.15,
    val fullSheddingRatio: Double = 2.0,
    val sheddingSampleAlpha: Double = 0.2,
    val sheddingDecayAlpha: Double = 0.2,
    val sampleIntervalNs: Long = 250_000_000L,
) {
    init {
        require(0 <= minSamplesForBaseline) { "minSamplesForBaseline must be non-negative" }
        require(0.0 <= ratioSlack) { "ratioSlack must be non-negative" }
        require(1.0 + ratioSlack < fullSheddingRatio) { "fullSheddingRatio must be greater than 1.0 + ratioSlack" }
        require(0.0 < sheddingSampleAlpha && sheddingSampleAlpha <= 1.0) { "sheddingSampleAlpha must be in (0, 1]" }
        require(0.0 < sheddingDecayAlpha && sheddingDecayAlpha <= 1.0) { "sheddingDecayAlpha must be in (0, 1]" }
        require(0L <= sampleIntervalNs) { "sampleIntervalNs must be non-negative" }
        require(0L <= minBaselineMs) { "minBaselineMs must be non-negative" }
    }
}
