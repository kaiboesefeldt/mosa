package straightway.mosa.defense

class CombinedSheddingDecider(
    private val subDeciders: List<SheddingDecider>,
) : SheddingDecider {
    override fun shouldShed() =
        subDeciders
            .map { it.shouldShed() }
            .maxByOrNull { it.sheddingFactor }
            ?: SheddingDecider.Result.NO_SHEDDING
}
