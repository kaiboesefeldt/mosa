package straightway.mosa.legal.clearterms

data class ClearTermsRenderedBlock(
    val id: String,
    val segments: List<Segment>,
) {
    data class Segment(
        val text: String,
        val background: ClearTermsRenderBackground,
    )
}
