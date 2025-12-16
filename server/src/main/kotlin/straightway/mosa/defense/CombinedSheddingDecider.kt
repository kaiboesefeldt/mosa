package straightway.mosa.defense

class CombinedSheddingDecider(
    private val subDeciders: List<SheddingDecider>,
) : SheddingDecider {
    override fun shouldShed() =
        subDeciders
            .asSequence()
            .map { it.shouldShed() }
            .firstOrNull { it.shouldShed }
            ?: SheddingDecider.Result.NO_SHEDDING
}
