package straightway.mosa.legal.clearterms

sealed class ClearTermsJsonNode {
    data class ObjectNode(
        val entries: Map<String, ClearTermsJsonNode>,
    ) : ClearTermsJsonNode()

    data class ArrayNode(
        val items: List<ClearTermsJsonNode>,
    ) : ClearTermsJsonNode()

    sealed class ScalarNode : ClearTermsJsonNode() {
        data class StringNode(
            val value: String,
        ) : ScalarNode()

        data class NumberNode(
            val value: Double,
        ) : ScalarNode()

        data class BooleanNode(
            val value: Boolean,
        ) : ScalarNode()

        object NullNode : ScalarNode()
    }
}
