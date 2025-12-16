package straightway.mosa.defense

fun interface SheddingDecider {
    data class Result(
        val shouldShed: Boolean,
        val reason: String,
    ) {
        companion object {
            val NO_SHEDDING = Result(false, "")
        }
    }

    fun shouldShed(): Result
}
