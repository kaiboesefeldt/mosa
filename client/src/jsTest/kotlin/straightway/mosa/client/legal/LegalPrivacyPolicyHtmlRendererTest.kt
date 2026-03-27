package straightway.mosa.client.legal

import kotlin.test.Test
import kotlin.test.assertContains
import straightway.mosa.legal.clearterms.render.RenderBackground
import straightway.mosa.legal.clearterms.render.RenderedBlock

class LegalPrivacyPolicyHtmlRendererTest {
    @Test
    fun `renders inline pair with shared comparison group metadata`() {
        val renderer = LegalPrivacyPolicyHtmlRenderer()

        val html =
            renderer.render(
                listOf(
                    RenderedBlock(
                        id = "intro",
                        inlineWithNext = true,
                        segments = listOf(RenderedBlock.Segment("First ", RenderBackground.NONE)),
                    ),
                    RenderedBlock(
                        id = "followup",
                        segments = listOf(RenderedBlock.Segment("Second", RenderBackground.NONE)),
                    ),
                ),
            )

        assertContains(html, """class="legal-ct-inline-group"""")
        assertContains(html, """data-ct-group-id="intro|followup"""")
        assertContains(html, """data-ct-block-ids="intro followup"""")
    }
}
