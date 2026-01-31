package straightway.mosa.doc

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownRendererTest {
    @Test
    fun `render outputs h1 heading with rule`() {
        val lines = renderLines("# title").dropLastWhile { it.isEmpty() }

        assertEquals("TITLE", lines[0], "lines=$lines")
        assertEquals("=====", lines[1], "lines=$lines")
    }

    @Test
    fun `render outputs h2 heading and separates from paragraph`() {
        val lines = renderLines("## section\ntext")

        assertEquals("SECTION", lines[0], "lines=$lines")
        assertEquals("", lines[1], "lines=$lines")
        assertEquals("text", lines[2], "lines=$lines")
    }

    @Test
    fun `render inserts blank line before heading after paragraph`() {
        val lines = renderLines("text\n# head").dropLastWhile { it.isEmpty() }
        val textIndex = lines.indexOf("text")
        val headingIndex = lines.indexOf("HEAD")

        assertTrue(textIndex in lines.indices)
        assertTrue(headingIndex in lines.indices)
        assertEquals("", lines[textIndex + 1])
        assertEquals(textIndex + 2, headingIndex)
    }

    @Test
    fun `render avoids double blank lines between adjacent headings`() {
        val lines = renderLines("# A\n## B\n### C").dropLastWhile { it.isEmpty() }

        val hasDoubleBlank = lines.zipWithNext().any { (first, second) -> first.isEmpty() && second.isEmpty() }
        assertTrue(!hasDoubleBlank)
    }

    @Test
    fun `render ignores whitespace-only content`() {
        val text = renderText(" \n\n\t")

        assertEquals("", text)
    }

    @Test
    fun `render uses minimum rule length when h1 text is empty`() {
        val lines = renderLines("# ").dropLastWhile { it.isEmpty() }

        assertEquals("", lines[0], "lines=$lines")
        assertEquals("===", lines[1], "lines=$lines")
    }

    @Test
    fun `render handles document with all style variants`() {
        val markdown =
            """
            # Title
            ## Section
            ### Sub
            #### Minor
            ##### Tiny
            ###### Small

            Paragraph with *emphasis*, **strong**, and ~~strike~~.
            Inline code: `code` and a [link](https://example.com).

            - [x] done
            - [ ] todo

            | head | value |
            | ---- | ----- |
            | one  | two   |

            ```kotlin
            val x = 1
            ```
            """.trimIndent()

        val text = renderText(markdown)

        assertEquals(
            """
            TITLE
            =====

            SECTION

            Sub

            Minor

            Tiny

            Small

            
            Paragraph with emphasis, strong, and strike. Inline code: code and a link
            (https://example.com).

             • ☑ done
             • ☐ todo

            ┌──────┬───────┐
            │ head │ value │
            ╞══════╪═══════╡
            │ one  │ two   │
            └──────┴───────┘

            ╭─────────╮
            │val x = 1│
            ╰─────────╯
            """.trimIndent(),
            text,
        )
    }

    private fun renderText(markdown: String): String {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        val printStream = PrintStream(output, true, Charsets.UTF_8)
        System.setOut(printStream)
        try {
            MarkdownRenderer().render(markdown)
        } finally {
            System.setOut(originalOut)
        }
        return output
            .toString(Charsets.UTF_8)
            .replace(Regex("\\u001B\\[[;\\d]*m"), "")
            .lineSequence()
            .map { it.removeSuffix("\r").trimEnd() }
            .joinToString("\n")
            .trimEnd()
    }

    private fun renderLines(markdown: String): List<String> = renderText(markdown).split("\n")
}
