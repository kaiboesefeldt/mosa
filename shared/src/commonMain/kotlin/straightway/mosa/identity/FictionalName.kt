package straightway.mosa.identity

data class FictionalName(
    val givenNames: List<String> = emptyList(),
    val sureName: String = "",
    val addendum: String? = null,
) {
    override fun toString() = givenNames.joinToString(" ") + (addendum?.let { " $it'" } ?: " ") + sureName
}
