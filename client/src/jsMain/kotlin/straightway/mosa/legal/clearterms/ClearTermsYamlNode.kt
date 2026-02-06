package straightway.mosa.legal.clearterms

sealed class ClearTermsYamlNode {
    data class MapNode(val entries: Map<String, ClearTermsYamlNode>) : ClearTermsYamlNode()

    data class SequenceNode(val items: List<ClearTermsYamlNode>) : ClearTermsYamlNode()

    sealed class ScalarNode : ClearTermsYamlNode() {
        data class StringNode(val value: String) : ScalarNode()

        data class NumberNode(val value: Double) : ScalarNode()

        object NullNode : ScalarNode()
    }
}
