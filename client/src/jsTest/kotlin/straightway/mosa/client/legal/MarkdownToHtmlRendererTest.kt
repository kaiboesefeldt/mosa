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
        val markdown = "Value: ${LegalPrivacyPolicyService.MARKER_OPEN_YELLOW}10${LegalPrivacyPolicyService.MARKER_CLOSE}."

        val html = renderer.render(markdown)

        assertEquals("""<p>Value: <span class="legal-ct-bg-yellow">10</span>.</p>""", html)
    }

    @Test
    fun `renders light green and red clearterms markers`() {
        val markdown =
            "${LegalPrivacyPolicyService.MARKER_OPEN_LIGHT_GREEN}ok${LegalPrivacyPolicyService.MARKER_CLOSE} " +
                "${LegalPrivacyPolicyService.MARKER_OPEN_RED}risk${LegalPrivacyPolicyService.MARKER_CLOSE}"

        val html = renderer.render(markdown)

        assertEquals(
            """<p><span class="legal-ct-bg-light-green">ok</span> <span class="legal-ct-bg-red">risk</span></p>""",
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
