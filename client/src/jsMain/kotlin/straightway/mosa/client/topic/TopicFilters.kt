package straightway.mosa.client.topic

object TopicFilters {
    fun sanitize(filters: Map<String, Float>): Map<String, Float> =
        filters
            .filterKeys { it.isNotBlank() }
            .filterValues { it.isFinite() }
}
