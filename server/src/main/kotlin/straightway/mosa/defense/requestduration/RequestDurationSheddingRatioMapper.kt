package straightway.mosa.defense.requestduration

class RequestDurationSheddingRatioMapper(
    config: RequestDurationSheddingConfig,
) {
    private val sheddingRatioStart = 1.0 + config.ratioSlack
    private val sheddingRatioSpan = config.fullSheddingRatio - sheddingRatioStart

    fun targetSheddingFactor(ratio: Double): Double =
        when {
            !ratio.isFinite() -> 0.0
            ratio <= sheddingRatioStart -> 0.0
            else -> ((ratio - sheddingRatioStart) / sheddingRatioSpan).coerceIn(0.0, 1.0)
        }
}
