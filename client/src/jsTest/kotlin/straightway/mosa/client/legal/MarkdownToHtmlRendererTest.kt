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
}
