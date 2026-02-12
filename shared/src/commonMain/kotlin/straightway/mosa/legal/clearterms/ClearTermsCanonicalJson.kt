package straightway.mosa.legal.clearterms

class ClearTermsCanonicalJson(
    private val nodeRenderer: ClearTermsCanonicalNodeRenderer = ClearTermsCanonicalNodeRenderer(),
) {
    fun canonicalize(root: ClearTermsJsonNode.ObjectNode): String = nodeRenderer.render(root)
}
