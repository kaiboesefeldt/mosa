package straightway.mosa.defense

class SheddingState(
    var smoothedSheddingFactor: Double = 0.0,
) {
    @Volatile
    var cachedDecision: SheddingDecider.Result = SheddingDecider.Result.NO_SHEDDING
}
