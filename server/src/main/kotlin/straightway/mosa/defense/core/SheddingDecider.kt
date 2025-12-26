package straightway.mosa.defense.core

data class SheddingRequest(
    val method: String,
    val route: String,
)

fun interface SheddingDecider {
    data class Result(
        val sheddingFactor: Double,
        val reason: String,
    ) {
        companion object {
            val NO_SHEDDING = Result(0.0, "")
        }
    }

    suspend fun decideShedding(request: SheddingRequest): Result
}
