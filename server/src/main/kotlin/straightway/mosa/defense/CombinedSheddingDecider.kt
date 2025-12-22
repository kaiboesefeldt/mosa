package straightway.mosa.defense

class CombinedSheddingDecider(
    private val subDeciders: List<SheddingDecider>,
) : SheddingDecider {
    override suspend fun decideShedding(request: SheddingRequest) =
        subDeciders
            .map { it.decideShedding(request) }
            .maxByOrNull { it.sheddingFactor }
            ?: SheddingDecider.Result.NO_SHEDDING
}
