package straightway.mosa.client.legal

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownToHtmlRendererTest {
    private val renderer = MarkdownToHtmlRenderer()

    @Test
    fun `renders heading paragraphs and bullet lists`() {
        val markdown =
            """
            # Title

            Intro paragraph.

            - First
            - Second
            """.trimIndent()

        val html = renderer.render(markdown)

        assertEquals("<h1>Title</h1><p>Intro paragraph.</p><ul><li>First</li><li>Second</li></ul>", html)
    }

    @Test
    fun `escapes html in markdown text`() {
        val html = renderer.render("# <script>alert(1)</script>")

        assertEquals("<h1>&lt;script&gt;alert(1)&lt;/script&gt;</h1>", html)
    }

    @Test
    fun `renders clearterms highlight markers as spans`() {
        val markdown =
            "Value: ${LegalPrivacyPolicyService.MARKER_OPEN_YELLOW}${LegalPrivacyPolicyService.MARKER_OPEN_SUFFIX}10${LegalPrivacyPolicyService.MARKER_CLOSE}."

        val html = renderer.render(markdown)

        assertEquals(
            """<p>Value: <span class="legal-ct-bg-yellow legal-ct-baseline-diff" style="background-color: #fef3c7; color: #78350f;">10</span>.</p>""",
            html,
        )
    }

    @Test
    fun `renders yellow and red clearterms markers`() {
        val markdown =
            "${LegalPrivacyPolicyService.MARKER_OPEN_YELLOW}${LegalPrivacyPolicyService.MARKER_OPEN_SUFFIX}ok${LegalPrivacyPolicyService.MARKER_CLOSE} " +
                "${LegalPrivacyPolicyService.MARKER_OPEN_RED}${LegalPrivacyPolicyService.MARKER_OPEN_SUFFIX}risk${LegalPrivacyPolicyService.MARKER_CLOSE}"

        val html = renderer.render(markdown)

        assertEquals(
            """<p><span class="legal-ct-bg-yellow legal-ct-baseline-diff" style="background-color: #fef3c7; color: #78350f;">ok</span> <span class="legal-ct-bg-red legal-ct-baseline-diff" style="background-color: #fee2e2; color: #7f1d1d;">risk</span></p>""",
            html,
        )
    }

    @Test
    fun `renders highlight markers with block id when wrapped in block markers`() {
        val markdown =
            "Operator: ${LegalPrivacyPolicyService.MARKER_OPEN_RED}:operator${LegalPrivacyPolicyService.MARKER_OPEN_SUFFIX}" +
                "Mosa${LegalPrivacyPolicyService.MARKER_CLOSE}"

        val html = renderer.render(markdown)

        assertEquals(
            """<p>Operator: <span data-ct-block-id="operator" class="legal-ct-bg-red legal-ct-baseline-diff" style="background-color: #fee2e2; color: #7f1d1d;">Mosa</span></p>""",
            html,
        )
    }

    @Test
    fun `treats invalid heading syntax as paragraph`() {
        val html = renderer.render("#No heading")

        assertEquals("<p>#No heading</p>", html)
    }

    @Test
    fun `renders star list syntax`() {
        val markdown =
            """
            * One
            * Two
            """.trimIndent()

        val html = renderer.render(markdown)

        assertEquals("<ul><li>One</li><li>Two</li></ul>", html)
    }
}
