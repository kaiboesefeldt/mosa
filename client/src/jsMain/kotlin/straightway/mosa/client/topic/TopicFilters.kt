package straightway.mosa.client.topic

object TopicFilters {
    fun sanitize(filters: Map<String, String>): Map<String, String> =
        filters
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, value) -> value.trim() }
            .filterValues { it.isNotEmpty() }
}
