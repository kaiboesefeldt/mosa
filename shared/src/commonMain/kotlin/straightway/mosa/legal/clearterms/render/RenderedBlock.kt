package straightway.mosa.legal.clearterms.render

data class RenderedBlock(
    val id: String,
    val isBaselineOverride: Boolean = false,
    val inlineWithNext: Boolean = false,
    val segments: List<Segment>,
) {
    data class Segment(
        val text: String,
        val background: RenderBackground,
        val isBaselineOverride: Boolean = false,
    )
}
