package straightway.mosa.defense

fun interface SheddingDecider {
    data class Result(
        val sheddingFactor: Double,
        val reason: String,
    ) {
        companion object {
            val NO_SHEDDING = Result(0.0, "")
        }
    }

    fun shouldShed(): Result
}
