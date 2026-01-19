package straightway.mosa.identity

data class FictionalName(
    val givenNames: List<String> = emptyList(),
    val sureName: String = "",
    val addendum: String? = null,
) {
    override fun toString() = givenNames.joinToString(" ") + (addendum?.takeIf { it.isNotBlank() }?.let { " $it'" } ?: " ") + sureName

    operator fun plus(other: FictionalName) =
        FictionalName(
            givenNames = givenNames + other.givenNames,
            sureName = listOf(sureName, other.sureName).filter { it.isNotBlank() }.joinToString(" "),
            addendum = mergeAddendum(addendum, other.addendum),
        )

    private fun mergeAddendum(
        left: String?,
        right: String?,
    ): String? {
        val merged = listOfNotNull(left, right).filter { it.isNotBlank() }
        return if (merged.isEmpty()) null else merged.joinToString("-")
    }
}
